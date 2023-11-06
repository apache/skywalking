/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * TerminalFriendlyTable represents a two columns table, each column accepts the String type.
 * It provides the {@link #toString()} to return two aligned column tables for the terminal output.
 */
@RequiredArgsConstructor
public class TerminalFriendlyTable {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

    /**
     * The description of the table.
     */
    private final String description;

    /**
     * Rows of the table.
     */
    private final List<Row> rows = new ArrayList<>(20);
    private int maxLengthOfCol1 = 0;
    private int maxLengthOfCol2 = 0;

    public void addRow(Row row) {
        boolean replaced = false;
        for (int i = 0; i < rows.size(); i++) {
            final Row e = rows.get(i);
            if (e.col1.equals(row.col1)) {
                e.col2 = row.col2;
                replaced = true;
                row = e;
                break;
            }
        }
        if (!replaced) {
            rows.add(row);
        }
        if (row.col1.length() > maxLengthOfCol1) {
            maxLengthOfCol1 = row.col1.length();
        }
        if (row.col2 != null && row.col2.length() > maxLengthOfCol2) {
            maxLengthOfCol2 = row.col2.length();
        }
    }

    @Override
    public String toString() {
        rows.sort(Comparator.comparing(a -> a.col1));
        StringBuilder output = new StringBuilder(description).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        String format = "%-" + (maxLengthOfCol1 + 3) + "s |   %-" + maxLengthOfCol2 + "s";
        rows.forEach(row -> {
            output.append(String.format(format, row.getCol1(), row.getCol2())).append(LINE_SEPARATOR);
        });
        return output.toString();
    }

    @Getter
    public static final class Row {
        private final String col1;
        private String col2;

        public Row(final String col1, final String col2) {
            if (col1 == null) {
                throw new IllegalArgumentException("Column 1 can't be null.");
            }
            this.col1 = col1;
            this.col2 = col2;
        }
    }
}
