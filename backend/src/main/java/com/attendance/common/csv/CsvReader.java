package com.attendance.common.csv;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader: supports commas, double-quoted fields, and
 * escaped quotes (""). Does not stream — buffers the whole file into rows.
 * Suitable for bounded admin uploads, not arbitrary large data.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<List<String>> readAll(Reader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        cell.append('"');
                    } else {
                        inQuotes = false;
                        if (next == -1) {
                            current.add(cell.toString());
                            cell.setLength(0);
                            if (!current.isEmpty()) {
                                rows.add(current);
                            }
                            return rows;
                        }
                        c = (char) next;
                        if (c == ',') {
                            current.add(cell.toString());
                            cell.setLength(0);
                        } else if (c == '\n') {
                            current.add(cell.toString());
                            cell.setLength(0);
                            rows.add(current);
                            current = new ArrayList<>();
                        } else if (c == '\r') {
                            // peek for \n
                        } else {
                            cell.append(c);
                        }
                    }
                } else {
                    cell.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    current.add(cell.toString());
                    cell.setLength(0);
                } else if (c == '\n') {
                    current.add(cell.toString());
                    cell.setLength(0);
                    rows.add(current);
                    current = new ArrayList<>();
                } else if (c == '\r') {
                    // skip; \n will follow on most platforms
                } else {
                    cell.append(c);
                }
            }
        }
        if (cell.length() > 0 || !current.isEmpty()) {
            current.add(cell.toString());
            rows.add(current);
        }
        return rows;
    }
}
