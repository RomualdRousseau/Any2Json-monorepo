package com.github.romualdrousseau.any2json.intelli.header;

import java.util.List;

import com.github.romualdrousseau.any2json.Row;
import com.github.romualdrousseau.any2json.base.BaseCell;
import com.github.romualdrousseau.any2json.intelli.CompositeTable;

public class MetaHeader extends CompositeHeader {

    public MetaHeader(final CompositeTable table, final BaseCell cell) {
        super(table, cell);
    }

    private MetaHeader(final MetaHeader parent) {
        this(parent.getTable(), parent.getCell());
    }

    @Override
    public String getName() {
        if (this.name == null) {
            final String v1 = this.getCell().getValue();
            final String v2 = this.getPivotEntityString().orElseGet(() -> this.getLayoutClassifier().getEntityList().anonymize(v1));
            this.name = this.getLayoutClassifier().getStopWordList().removeStopWords(v2);
        }
        return this.name;
    }

    @Override
    public String getValue() {
        if (this.value == null) {
            final String v1 = this.getCell().getValue();
            final String v2 = this.getLayoutClassifier().getEntityList().find(v1);
            this.value = (v2 == null) ? v1 : v2;
        }
        return this.value;
    }

    @Override
    public BaseCell getCellAtRow(final Row row) {
        if (this.transformedCell == null) {
            final String v1 = this.getCell().getValue();
            final String v2 = this.getLayoutClassifier().getEntityList().find(v1);
            if (v2 == null) {
                this.transformedCell = this.getCell();
            } else {
                this.transformedCell = new BaseCell(v2, this.getCell().getColumnIndex(),
                        this.getCell().getMergedCount(), this.getCell().getRawValue(), this.getTable().getSheet().getClassifierFactory());
            }
        }
        return this.transformedCell;
    }

    @Override
    public CompositeHeader clone() {
        return new MetaHeader(this);
    }

    @Override
    public List<String> entities() {
        return null;
    }

    private String name;
    private String value;
    private BaseCell transformedCell;
}
