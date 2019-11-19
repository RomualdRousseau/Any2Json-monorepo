package com.github.romualdrousseau.any2json.v2;

import java.util.ArrayList;

import com.github.romualdrousseau.any2json.v2.base.Cell;
import com.github.romualdrousseau.any2json.v2.base.Row;
import com.github.romualdrousseau.any2json.v2.base.Table;
import com.github.romualdrousseau.any2json.v2.layex.IStream;

public class TableStream implements IStream<Cell, TableStream.Cursor> {

    public class Cursor {

        public Cursor(int colIndex, int rowIndex) {
            this.colIndex = colIndex;
            this.rowIndex = rowIndex;
        }

        public int getColIndex() {
            return this.colIndex;
        }

        public int getRowIndex() {
            return this.rowIndex;
        }

        private int colIndex;
        private int rowIndex;
    }

    public TableStream(ITable table) {
        this.stack = new ArrayList<Cursor>();
        this.table = (Table) table;
        this.colIndex = 0;
        this.rowIndex = 0;
    }

    @Override
    public Cell read() {
        if(this.rowIndex >= this.table.getNumberOfRows()) {
            return Cell.EndOfStream;
        }

        if(this.colIndex >= this.table.getNumberOfColumns()) {
            this.colIndex = 0;
            this.rowIndex++;
            return Cell.EndOfRow;
        }

        Row row = this.table.getRowAt(this.rowIndex);
        if(row.isEmpty()) {
            this.rowIndex++;
            return Cell.EndOfRow;
        }

        Cell cell = row.getCellAt(colIndex);
        colIndex += cell.getMergedCount();

        return cell;
    }

    @Override
    public Cell peek() {
        if(this.rowIndex >= this.table.getNumberOfRows()) {
            return Cell.EndOfStream;
        }

        if(this.colIndex >= this.table.getNumberOfColumns()) {
            return Cell.EndOfRow;
        }

        Row row = this.table.getRowAt(this.rowIndex);
        if(row.isEmpty()) {
            return Cell.EndOfRow;
        }

        return row.getCellAt(colIndex);
    }

    @Override
    public void push() {
        this.stack.add(new Cursor(this.colIndex, this.rowIndex));
    }

    @Override
    public Cursor pop() {
        return this.stack.remove(this.stack.size() - 1);
    }

    @Override
    public void seek(Cursor c) {
        this.colIndex = c.getColIndex();
        this.rowIndex = c.getRowIndex();
    }

    private ArrayList<Cursor> stack;
    private Table table;
    private int colIndex;
    private int rowIndex;
}
