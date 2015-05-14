/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Modified BSD License
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at:
//
// http://opensource.org/licenses/BSD-3-Clause
*/
package sqlline;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sqlline.Rows.Row;

/**
 * OutputFormat for a pretty, table-like format.
 */
class TableOutputFormat implements OutputFormat {
  private final SqlLine sqlLine;
  private final int resizeFrequency;

  public TableOutputFormat(SqlLine sqlLine) {
    this.sqlLine = sqlLine;
    this.resizeFrequency = sqlLine.getOpts().getHeaderInterval() == 0
        ? 50 : sqlLine.getOpts().getHeaderInterval();
  }

  /**
   * Class to provide resizing to the row output as the data comes in.
   */
  private class ResizingRowsProvider implements Iterator<Rows.Row> {
    private final List<Row> buffer = new ArrayList<Row>(resizeFrequency);
    private int current = 0;
    private final Rows rows;

    public ResizingRowsProvider(Rows rows) {
      super();
      this.rows = rows;
    }

    public boolean hasNext() {
      return current + 1 < buffer.size() || rows.hasNext();
    }

    void normalizeWidths(List<Row> list) {
      int[] max = null;
      for (Row row : list) {
        if (max == null) {
          max = new int[row.values.length];
        }

        for (int j = 0; j < max.length; j++) {
          max[j] = Math.max(max[j], row.sizes[j] + 1);
        }
      }

      for (Row row : list) {
        row.sizes = max;
      }
    }

    public Row next() {
      current++;
      if (current < buffer.size()) {
        return buffer.get(current);
      } else {
        buffer.clear();
        int i = 0;
        while (i < resizeFrequency && rows.hasNext()) {
          buffer.add(rows.next());
        }
        normalizeWidths(buffer);

        current = 1;
        if (buffer.isEmpty()) {
          return null;
        } else {
          return buffer.get(0);
        }
      }

    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

  }
  public int print(Rows rows) {
    int index = 0;
    ColorBuffer header = null;
    ColorBuffer headerCols = null;
    final int width = sqlLine.getOpts().getMaxWidth() - 4;

    ResizingRowsProvider provider = new ResizingRowsProvider(rows);

    for (; provider.hasNext();) {
      Rows.Row row = provider.next();
      ColorBuffer cbuf = getOutputString(rows, row);
      cbuf = cbuf.truncate(width);

      // Print header if at that time.
      if ((index == 0)
          || (sqlLine.getOpts().getHeaderInterval() > 0
              && (index % sqlLine.getOpts().getHeaderInterval() == 0)
              && sqlLine.getOpts().getShowHeader())) {

        StringBuilder h = new StringBuilder();
        for (int j = 0; j < row.sizes.length; j++) {
          for (int k = 0; k < row.sizes[j]; k++) {
            h.append('-');
          }
          h.append("-+-");
        }

        headerCols = cbuf;
        header =
            sqlLine.getColorBuffer().green(h.toString()).truncate(
                headerCols.getVisibleLength());

        printRow(header, true);
        printRow(headerCols, false);
        printRow(header, true);
      }

      if (index != 0) { // don't output the header twice
        printRow(cbuf, false);
      }

      index++;
    }

    if (header != null && sqlLine.getOpts().getShowHeader()) {
      printRow(header, true);
    }

    return index - 1;
  }

  void printRow(ColorBuffer cbuff, boolean header) {
    if (header) {
      sqlLine.output(sqlLine.getColorBuffer()
          .green("+-")
          .append(cbuff)
          .green("-+"));
    } else {
      sqlLine.output(sqlLine.getColorBuffer()
          .green("| ")
          .append(cbuff)
          .green(" |"));
    }
  }

  public ColorBuffer getOutputString(Rows rows, Rows.Row row) {
    return getOutputString(rows, row, " | ");
  }

  ColorBuffer getOutputString(Rows rows, Rows.Row row, String delim) {
    ColorBuffer buf = sqlLine.getColorBuffer();

    for (int i = 0; i < row.values.length; i++) {
      if (buf.getVisibleLength() > 0) {
        buf.green(delim);
      }

      ColorBuffer v;

      if (row.isMeta) {
        v = sqlLine.getColorBuffer().center(row.values[i], row.sizes[i]);
        if (rows.isPrimaryKey(i)) {
          buf.cyan(v.getMono());
        } else {
          buf.bold(v.getMono());
        }
      } else {
        v = sqlLine.getColorBuffer().pad(row.values[i], row.sizes[i]);
        if (rows.isPrimaryKey(i)) {
          buf.cyan(v.getMono());
        } else {
          buf.append(v.getMono());
        }
      }
    }

    if (row.deleted) { // make deleted rows red
      buf = sqlLine.getColorBuffer().red(buf.getMono());
    } else if (row.updated) { // make updated rows blue
      buf = sqlLine.getColorBuffer().blue(buf.getMono());
    } else if (row.inserted) { // make new rows green
      buf = sqlLine.getColorBuffer().green(buf.getMono());
    }

    return buf;
  }
}

// End TableOutputFormat.java
