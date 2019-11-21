package com.github.romualdrousseau.any2json.v2.intelli;

import java.util.ArrayList;
import java.util.List;

import com.github.romualdrousseau.any2json.v2.ITable;
import com.github.romualdrousseau.any2json.ITagClassifier;
import com.github.romualdrousseau.any2json.v2.SheetBitmap;
import com.github.romualdrousseau.any2json.v2.TableStream;
import com.github.romualdrousseau.any2json.v2.base.Row;
import com.github.romualdrousseau.any2json.v2.base.Sheet;
import com.github.romualdrousseau.any2json.v2.base.Table;
import com.github.romualdrousseau.any2json.v2.layex.Layex;
import com.github.romualdrousseau.any2json.v2.layex.LayexMatcher;
import com.github.romualdrousseau.any2json.v2.util.TableGraph;
import com.github.romualdrousseau.any2json.v2.util.Visitable;
import com.github.romualdrousseau.shuju.cv.Filter;
import com.github.romualdrousseau.shuju.cv.ISearchBitmap;
import com.github.romualdrousseau.shuju.cv.SearchPoint;
import com.github.romualdrousseau.shuju.cv.Template;
import com.github.romualdrousseau.shuju.cv.templatematching.TemplateMatcher;
import com.github.romualdrousseau.shuju.cv.templatematching.shapeextractor.RectangleExtractor;

public abstract class IntelliSheet extends Sheet {

    public final static int SEPARATOR_ROW_THRESHOLD = 10;

    @Override
    public ITable getTable(ITagClassifier classifier) {
        List<LayexMatcher> metaLayexes = new ArrayList<LayexMatcher>();
        List<LayexMatcher> dataLayexes = new ArrayList<LayexMatcher>();
        this.compileLayexes(metaLayexes, dataLayexes);

        List<Table> tables = this.findAllTables(classifier);
        List<DataTable> dataTables = this.getDataTables(tables, dataLayexes);
        List<MetaTable> metaTables = this.getMetaTables(tables, metaLayexes);
        TableGraph root = this.buildTableGraph(metaTables, dataTables);

        return new IntelliTable(root);
    }

    private void compileLayexes(List<LayexMatcher> metaLayexes, List<LayexMatcher> dataLayexes) {
        // Simple key value table
        metaLayexes.add(new Layex("(v[v|m]$)+").compile());

        // Simple table
        dataLayexes.add(new Layex("(v{2}v+$)([v|m|s]{2}[v|m|s]+$)+").compile());

        // Complex table with meta and pivot
        dataLayexes.add(new Layex("(ms*$v+m*$)([v|m|s]{2}[v|m|s]+$)+").compile());
        // dataLayexes.add(new Layex("(ms*$v+m*$)([v|m|s]{2}[v|m|s]+$)+([v|m|s]{2}$)?").compile());

        for (LayexMatcher layex : metaLayexes) {
            System.out.println(layex.toString());
        }

        for (LayexMatcher layex : dataLayexes) {
            System.out.println(layex.toString());
        }
    }

    private TableGraph buildTableGraph(List<MetaTable> metaTables, List<DataTable> dataTables) {
        TableGraph root = new TableGraph();

        for (Visitable e : metaTables) {
            e.setVisited(false);
        }

        // First attach all not snapped metaTables to the root nodes
        for (MetaTable metaTable : metaTables) {
            if (!isSnapped(metaTable, dataTables)) {
                root.addChild(new TableGraph(metaTable));
                metaTable.setVisited(true);
            }
        }

        // Second attach all snapped metaTables to the closest nodes
        for (MetaTable metaTable : metaTables) {
            if (metaTable.isVisited()) {
                continue;
            }

            TableGraph parent = findClosestMetaGraph(root, metaTable, 0, 0);
            parent.addChild(new TableGraph(metaTable));
            metaTable.setVisited(true);
        }

        // Third attach datatables to the closest metadatas
        for (DataTable dataTable : dataTables) {
            TableGraph parent = findClosestMetaGraph(root, dataTable, 0, 1);
            parent.addChild(new TableGraph(dataTable));
        }

        return root;
    }

    private List<DataTable> getDataTables(List<Table> tables, List<LayexMatcher> dataLayexes) {
        ArrayList<DataTable> result = new ArrayList<DataTable>();

        for (Visitable e : tables) {
            e.setVisited(false);
        }

        for (Table table : tables) {
            for (LayexMatcher dataLayex : dataLayexes) {
                if (dataLayex.match(new TableStream(table), null)) {
                    result.add(new DataTable(table, dataLayex));
                    table.setVisited(true);
                }
            }
        }

        return result;
    }

    private List<MetaTable> getMetaTables(List<Table> tables, List<LayexMatcher> metaLayexes) {
        ArrayList<MetaTable> result = new ArrayList<MetaTable>();

        for (Table table : tables) {
            if (table.isVisited()) {
                continue;
            }

            boolean foundMatch = false;
            for (LayexMatcher metaLayex : metaLayexes) {
                if (metaLayex.match(new TableStream(table), null)) {
                    result.add(new MetaTable(table, metaLayex));
                    foundMatch = true;
                }
            }
            if (!foundMatch) {
                result.add(new MetaTable(table));
            }

            table.setVisited(true);
        }

        return result;
    }

    public List<Table> findAllTables(ITagClassifier classifier) {
        ArrayList<Table> result = new ArrayList<Table>();

        List<SearchPoint[]> rectangles = findAllRectangles(classifier.getSampleCount(), this.getLastRowNum());

        for (SearchPoint[] rectangle : rectangles) {
            int firstColumnNum = rectangle[0].getX();
            int firstRowNum = rectangle[0].getY();
            int lastColumnNum = Math.max(rectangle[1].getX(), this.getLastColumnNum(firstColumnNum, firstRowNum));
            int lastRowNum = rectangle[1].getY();

            Table table = new Table(this, firstColumnNum, firstRowNum, lastColumnNum, lastRowNum, classifier);

            boolean isSplitted = false;
            for (int i = 0; i < table.getNumberOfRows(); i++) {
                Row row = table.getRowAt(i);
                if (row.sparsity() >= 0.5) {
                    int currRowNum = table.getFirstRow() + i;
                    if (firstRowNum <= (currRowNum - 1)) {
                        result.add(new Table(table, firstRowNum, currRowNum - 1));
                    }
                    result.add(new Table(table, currRowNum, currRowNum));
                    firstRowNum = currRowNum + 1;
                    isSplitted |= true;
                }
            }

            if (!isSplitted) {
                result.add(table);
            } else if (firstRowNum <= lastRowNum) {
                result.add(new Table(table, firstRowNum, lastRowNum));
            }
        }

        return result;
    }

    private List<SearchPoint[]> findAllRectangles(int columns, int rows) {
        ISearchBitmap original = new SheetBitmap(this, columns, rows);
        ISearchBitmap filtered = original.clone();

        final Filter filter = new Filter(new Template(new float[][] { { 0, 0, 0 }, { 1, 1, 0 }, { 0, 0, 0 } }));
        filter.apply(original, filtered, 0.5);
        List<SearchPoint[]> rectangles = new RectangleExtractor().extractAll(filtered);

        for (SearchPoint[] rectangle : rectangles) {
            rectangle[0] = new SearchPoint(Math.max(0, rectangle[0].getX() - 1), rectangle[0].getY(),
                    rectangle[0].getSAD());
        }

        final TemplateMatcher pointTemplate = new TemplateMatcher(
                new Template(new float[][] { { 0, 0, 0 }, { 0, 1, 1 }, { 0, 0, 0 } }));
        List<SearchPoint> points = pointTemplate.matchAll(filtered, 0, 0, filtered.getWidth(), filtered.getHeight(),
                0.9);
        for (SearchPoint point : points) {
            rectangles
                    .add(new SearchPoint[] { point, new SearchPoint(point.getX() + 1, point.getY(), point.getSAD()) });
        }

        rectangles = SearchPoint.TrimInX(SearchPoint.MergeInX(SearchPoint.RemoveOverlaps(rectangles)), original);

        return rectangles;
    }

    private boolean isSnapped(MetaTable metaTable, List<DataTable> dataTables) {
        for (DataTable dataTable : dataTables) {
            if (distanceBetweenTables(metaTable, dataTable) == 0) {
                return true;
            }
        }
        return false;
    }

    private TableGraph findClosestMetaGraph(TableGraph root, Table table, int level, int maxLevel) {
        TableGraph result = root;

        if (level > maxLevel) {
            return result;
        }

        double minDist = Double.MAX_VALUE;
        for (TableGraph child : root.children()) {
            if (!(child.getTable() instanceof MetaTable)) {
                continue;
            }

            TableGraph grandChild = findClosestMetaGraph(child, table, level + 1, maxLevel);
            double dist1 = distanceBetweenTables(grandChild.getTable(), table);
            double dist2 = distanceBetweenTables(child.getTable(), table);

            if (dist1 < dist2) {
                if (dist1 < minDist) {
                    minDist = dist1;
                    result = grandChild;
                }
            } else {
                if (dist2 < minDist) {
                    minDist = dist2;
                    result = child;
                }
            }
        }

        return result;
    }

    private double distanceBetweenTables(Table table1, Table table2) {
        int vx = table2.getFirstColumn() - table1.getFirstColumn();
        int vy = table2.getFirstRow() - table1.getLastRow() - 1;
        if (vx >= 0 && vy >= 0) {
            return vx + vy;
        } else {
            return Double.MAX_VALUE;
        }
    }
}
