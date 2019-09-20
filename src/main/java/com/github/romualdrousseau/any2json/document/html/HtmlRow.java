package com.github.romualdrousseau.any2json.document.html;

import com.github.romualdrousseau.any2json.IHeader;
import com.github.romualdrousseau.any2json.TableRow;

class HtmlRow extends TableRow
{
	public HtmlRow(String[] cells, int groupId) {
        this.cells = cells;
        this.groupId = groupId;
	}

	public int getNumberOfCells() {
		return this.cells.length;
    }

    public int getNumberOfMergedCellsAt(int i) {
        return 1;
    }

	public String getCellValue(IHeader header) {
		if(header == null) {
			throw new IllegalArgumentException();
		}

		return getCellValueAt(header.getColumnIndex());
	}

	public String getCellValueAt(int i) {
		if(i < 0 || i >= getNumberOfCells()) {
			throw new ArrayIndexOutOfBoundsException(i);
		}

		return this.cells[i];
	}

	private String[] cells = null;
}
