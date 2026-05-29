package com.attendance.common.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer: comma-separated, CRLF line terminators, and
 * double-quote escaping of any field containing a comma, quote, CR or LF.
 *
 * <p>Output is deterministic given the same rows — golden-fixture tests rely on
 * that. No UTF-8 BOM is emitted so a strict CSV parser sees a clean first
 * header cell; Excel still imports UTF-8 correctly.
 */
public final class CsvWriter {

    private static final String CRLF = "\r\n";

    private CsvWriter() {
    }

    public static void write(Writer out, List<String> headers, List<List<String>> rows) throws IOException {
        writeRow(out, headers);
        for (List<String> row : rows) {
            writeRow(out, row);
        }
    }

    private static void writeRow(Writer out, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(cells.get(i)));
        }
        out.write(CRLF);
    }

    static String escape(String value) {
        String v = value == null ? "" : value;
        boolean mustQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!mustQuote) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
