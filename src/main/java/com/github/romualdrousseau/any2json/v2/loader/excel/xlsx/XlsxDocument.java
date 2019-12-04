package com.github.romualdrousseau.any2json.v2.loader.excel.xlsx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.github.romualdrousseau.any2json.v2.Document;
import com.github.romualdrousseau.any2json.v2.Sheet;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.model.StylesTable;

public class XlsxDocument implements Document {

    @Override
    public boolean open(final File excelFile, final String encoding) {
        try {
            this.opcPackage = OPCPackage.open(excelFile.getAbsolutePath(), PackageAccess.READ);
            final XSSFReader reader = new XSSFReader(this.opcPackage);
            final SharedStringsTable sharedStrings = reader.getSharedStringsTable();
            final StylesTable styles = reader.getStylesTable();

            final SheetIterator it = (SheetIterator) reader.getSheetsData();
            while (it.hasNext()) {
                InputStream sheetData = it.next();
                this.sheets.add(new XlsxSheet(it.getSheetName(), sheetData, sharedStrings, styles));
            }

            return true;
        } catch (IOException | OpenXML4JException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void close() {
        if (this.opcPackage != null) {
            try {
                this.opcPackage.close();
                this.opcPackage = null;
            } catch (final IOException ignore) {
            }
        }
    }

    @Override
    public int getNumberOfSheets() {
        return this.sheets.size();
    }

    @Override
    public Sheet getSheetAt(final int i) {
        return sheets.get(i).ensureDataLoaded();
    }

    private OPCPackage opcPackage;
    private final ArrayList<XlsxSheet> sheets = new ArrayList<XlsxSheet>();
}
