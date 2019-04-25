package com.github.romualdrousseau.any2json.document.text;

import java.util.List;
import java.util.ArrayList;

import com.github.romualdrousseau.any2json.ISheet;
import com.github.romualdrousseau.any2json.ITable;

class TextSheet implements ISheet
{
	public TextSheet(String name, ITable table) {
		this.name = name;
		this.table = table;
	}

	public String getName() {
		return this.name;
	}

	public ITable getTable() {
		return this.table;
	}

	public ITable findTable(int headerColumns, int headerRows) {
		return this.table;
    }

    public List<ITable> findTables(int headerColumns, int headerRows) {
        ArrayList<ITable> result = new ArrayList<ITable>();
        result.add(this.table);
        return result;
    }

	private String name;
	private ITable table;
}
