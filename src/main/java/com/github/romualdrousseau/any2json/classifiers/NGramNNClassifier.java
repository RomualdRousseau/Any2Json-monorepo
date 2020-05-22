package com.github.romualdrousseau.any2json.classifiers;

import java.util.ArrayList;
import java.util.List;

import com.github.romualdrousseau.any2json.DocumentFactory;
import com.github.romualdrousseau.any2json.ITagClassifier;
import com.github.romualdrousseau.any2json.layex.Layex;
import com.github.romualdrousseau.any2json.layex.LayexMatcher;
import com.github.romualdrousseau.shuju.DataRow;
import com.github.romualdrousseau.shuju.DataSet;
import com.github.romualdrousseau.shuju.json.JSON;
import com.github.romualdrousseau.shuju.json.JSONArray;
import com.github.romualdrousseau.shuju.json.JSONObject;
import com.github.romualdrousseau.shuju.math.Tensor1D;
import com.github.romualdrousseau.shuju.math.Tensor2D;
import com.github.romualdrousseau.shuju.ml.nn.Layer;
import com.github.romualdrousseau.shuju.ml.nn.Loss;
import com.github.romualdrousseau.shuju.ml.nn.Model;
import com.github.romualdrousseau.shuju.ml.nn.Optimizer;
import com.github.romualdrousseau.shuju.ml.nn.activation.LeakyRelu;
import com.github.romualdrousseau.shuju.ml.nn.activation.Softmax;
import com.github.romualdrousseau.shuju.ml.nn.layer.builder.ActivationBuilder;
import com.github.romualdrousseau.shuju.ml.nn.layer.builder.BatchNormalizerBuilder;
import com.github.romualdrousseau.shuju.ml.nn.layer.builder.DenseBuilder;
import com.github.romualdrousseau.shuju.ml.nn.loss.SoftmaxCrossEntropy;
import com.github.romualdrousseau.shuju.ml.nn.optimizer.builder.OptimizerAdamBuilder;
import com.github.romualdrousseau.shuju.nlp.NgramList;
import com.github.romualdrousseau.shuju.nlp.RegexList;
import com.github.romualdrousseau.shuju.nlp.StopWordList;
import com.github.romualdrousseau.shuju.nlp.StringList;

public class NGramNNClassifier implements ITagClassifier {
    public static final int BATCH_SIZE = 64;

    private final NgramList ngrams;
    private final RegexList entities;
    private final StopWordList stopwords;
    private final StringList tags;
    private String[] requiredTags;
    private Model model;
    private Optimizer optimizer;
    private Loss loss;
    private float accuracy;
    private float mean;
    private List<LayexMatcher> metaLayexes;
    private List<LayexMatcher> dataLayexes;

    private final static String[] metaLayexesDefault = { "(v.$)+" };

    private final static String[] dataLayexesDefault = {
            "((e.*$)(vS.+$))(()(.{3,}$))+(.{2}$)?",
            "((v.*$)(vS.+$))((.{2}$)(.{3,}$)+)+(.{2}$)?",
            "(()(ES.+$))((sS.+$)(S.{2,}$)+)+(.{2}$)?",
            "(()(ES.+$))(()(.{3,}$))+(.{2}$)?"
        };

    public NGramNNClassifier(final NgramList ngrams, final RegexList entities, final StopWordList stopwords, final StringList tags) {
        this(ngrams, entities, stopwords, tags, null);
    }

    public NGramNNClassifier(final NgramList ngrams, final RegexList entities, final StopWordList stopwords, final StringList tags, final String[] requiredTags) {
        this(ngrams, entities, stopwords, tags, requiredTags, metaLayexesDefault, dataLayexesDefault);
    }

    public NGramNNClassifier(final NgramList ngrams, final RegexList entities, final StopWordList stopwords, final StringList tags, final String[] requiredTags, final String[] metaLayexes, final String[] dataLayexes) {
        this.accuracy = 0.0f;
        this.mean = 1.0f;
        this.ngrams = ngrams;
        this.entities = entities;
        this.stopwords = stopwords;
        this.tags = tags;
        this.requiredTags = requiredTags;
        this.metaLayexes = new ArrayList<LayexMatcher>();
        this.dataLayexes = new ArrayList<LayexMatcher>();

        if (metaLayexes != null) {
            for (final String layex : metaLayexes) {
                this.metaLayexes.add(new Layex(layex).compile());
            }
        }

        if (dataLayexes != null) {
            for (final String layex : dataLayexes) {
                this.dataLayexes.add(new Layex(layex).compile());
            }
        }

        this.buildModel();
    }

    public NGramNNClassifier(final JSONObject json) {
        this(new NgramList(json.getJSONObject("ngrams")),
                new RegexList(json.getJSONObject("entities")),
                new StopWordList(json.getJSONArray("stopwords")),
                new StringList(json.getJSONObject("tags")),
                null,
                null,
                null);

        final JSONArray requiredTags = json.getJSONObject("tags").getJSONArray("requiredTypes");
        if (requiredTags != null && requiredTags.size() > 0) {
            this.requiredTags = new String[requiredTags.size()];
            for (int i = 0; i < requiredTags.size(); i++) {
                this.requiredTags[i] = requiredTags.getString(i);
            }
        }

        final JSONArray layexes = json.getJSONArray("layexes");
        if (layexes != null) {
            for (int i = 0; i < layexes.size(); i++) {
                final JSONObject layex = layexes.getJSONObject(i);
                if (layex.getString("type").equals("META")) {
                    this.metaLayexes.add(new Layex(layex.getString("layex")).compile());
                } else if (layex.getString("type").equals("DATA")) {
                    this.dataLayexes.add(new Layex(layex.getString("layex")).compile());
                }
            }
        }

        this.model.fromJSON(json.getJSONArray("model"));
    }

    public int getSampleCount() {
        return DocumentFactory.DEFAULT_SAMPLE_COUNT;
    }

    public StopWordList getStopWordList() {
        return this.stopwords;
    }

    public RegexList getEntityList() {
        return this.entities;
    }

    public NgramList getWordList() {
        return this.ngrams;
    }

    public StringList getTagList() {
        return this.tags;
    }

    public String[] getRequiredTagList() {
        return this.requiredTags;
    }

    public List<LayexMatcher> getMetaLayexes() {
        return this.metaLayexes;
    }

    public List<LayexMatcher> getDataLayexes() {
        return this.dataLayexes;
    }

    public Model getModel() {
        return this.model;
    }

    public float getMean() {
        return this.mean;
    }

    public float getAccuracy() {
        return this.accuracy;
    }

    public void fit(final DataSet trainingSet, final DataSet validationSet) {
        final float n = trainingSet.rows().size();

        this.accuracy = 0.0f;
        this.mean = 0.0f;

        if (n == 0.0f) {
            return;
        }

        // Train

        final DataSet reducedSet = trainingSet.shuffle().subset(0, (int) (n * 0.8f));
        for (int i = 0; i < reducedSet.rows().size();) {

            this.optimizer.zeroGradients();

            final int batchSize = Math.min(reducedSet.rows().size() - i, BATCH_SIZE);
            for (int j = 0; j < batchSize; j++) {
                final DataRow row = reducedSet.rows().get(i++);

                final Tensor2D input = new Tensor2D(row.featuresAsOneVector(), false);
                final Tensor2D target = new Tensor2D(row.label(), false);

                final Layer output = this.model.model(input, true);
                final Loss loss = this.loss.loss(output, target);

                this.optimizer.minimize(loss);
            }

            this.optimizer.step();
        }

        // Validate

        for (final DataRow row : validationSet.rows()) {
            final Tensor2D input = new Tensor2D(row.featuresAsOneVector(), false);
            final Tensor2D target = new Tensor2D(row.label(), false);

            final Layer output = this.model.model(input);
            final Loss loss = this.loss.loss(output, target);

            final boolean isCorrect = output.detach().argmax(0, 0) == target.argmax(0, 0);
            this.accuracy += isCorrect ? 1 : 0;
            this.mean += loss.getValue().flatten(0, 0);
        }

        final float total = validationSet.rows().size();
        this.accuracy /= total;
        this.mean /= total;
    }

    public String predict(final DataRow row) {
        final Tensor2D input = new Tensor2D(row.featuresAsOneVector(), false);
        final Tensor2D output = this.model.model(input).detach();

        int tagIndex = output.argmax(0, 0);
        if (tagIndex >= this.tags.size()) {
            tagIndex = 0;
        }

        return this.tags.get(tagIndex);
    }

    public JSONObject toJSON() {
        final JSONArray requiredTags = JSON.newJSONArray();
        if (this.requiredTags != null) {
            for (int i = 0; i < this.requiredTags.length; i++) {
                requiredTags.append(this.requiredTags[i]);
            }
        }

        final JSONObject tags = this.tags.toJSON();
        tags.setJSONArray("requiredTypes", requiredTags);

        final JSONObject json = JSON.newJSONObject();
        json.setJSONObject("ngrams", this.ngrams.toJSON());
        json.setJSONObject("entities", this.entities.toJSON());
        json.setJSONArray("stopwords", this.stopwords.toJSON());
        json.setJSONObject("tags", tags);
        json.setJSONArray("model", this.model.toJSON());
        return json;
    }

    public String dumpDataSet(final DataSet dataset) {
        final StringBuilder result = new StringBuilder();
        result.append("============================ DUMP TRAININSET ============================\n");
        for (final DataRow row : dataset.rows()) {
            result.append(this.dumpDataRow(dataset, row));
        }
        result.append("================================== END ==================================\n");
        return result.toString();
    }

    public String dumpDataRow(final DataSet dataset, final DataRow row) {
        final StringBuilder result = new StringBuilder();

        Tensor1D v = row.features().get(0);
        boolean firstPass = true;
        for (int i = 0; i < v.shape[0]; i++) {
            if (v.get(i) == 1.0f) {
                final String e = this.getEntityList().get(i);
                if (e != null) {
                    if (firstPass) {
                        firstPass = false;
                    } else {
                        result.append(":");
                    }
                    result.append(e);
                }
            }
        }

        result.append(",");

        v = row.features().get(1);
        firstPass = true;
        for (int i = 0; i < v.shape[0]; i++) {
            if (v.get(i) == 1.0f) {
                final String w = this.getWordList().get(i);
                if (w != null) {
                    if (firstPass) {
                        firstPass = false;
                    } else {
                        result.append(":");
                    }
                    result.append(w);
                }
            }
        }

        result.append(",");

        v = row.features().get(2);
        firstPass = true;
        for (int i = 0; i < v.shape[0]; i++) {
            if (v.get(i) == 1.0f) {
                final String w = this.getWordList().get(i);
                if (w != null) {
                    if (firstPass) {
                        firstPass = false;
                    } else {
                        result.append(":");
                    }
                    result.append(w);
                }
            }
        }

        result.append(",");

        final Tensor1D l = row.label();

        firstPass = true;
        for (int i = 0; i < 16; i++) {
            if (l.get(i) == 1.0f) {
                if (firstPass) {
                    result.append(this.getTagList().get(i));
                    firstPass = false;
                } else {
                    result.append(":").append(this.getTagList().get(i));
                }
            }
        }

        result.append("\n");

        return result.toString();
    }

    private void buildModel() {
        final int inputCount = this.entities.getVectorSize() + 2 * this.ngrams.getVectorSize();
        final int hiddenCount = inputCount / 2;
        final int outputCount = this.tags.getVectorSize();

        this.model = new Model()
                .add(new DenseBuilder().setInputUnits(inputCount).setUnits(hiddenCount))
                .add(new ActivationBuilder().setActivation(new LeakyRelu()))
                .add(new BatchNormalizerBuilder())
                .add(new DenseBuilder().setUnits(outputCount))
                .add(new ActivationBuilder().setActivation(new Softmax()));

        this.optimizer = new OptimizerAdamBuilder().build(this.model);

        this.loss = new Loss(new SoftmaxCrossEntropy());
    }
}
