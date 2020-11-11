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

package org.apache.skywalking.oap.server.core.query.type;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;

/**
 * HeatMap represents the value distribution in the defined buckets.
 *
 * @since 8.0.0
 */
@Getter
public class HeatMap {
    private List<HeatMapColumn> values = new ArrayList<>(10);
    private List<Bucket> buckets = new ArrayList<>();

    public void addBucket(Bucket bucket) {
        this.buckets.add(bucket);
    }

    /**
     * Build one heatmap value column based on rawdata in the storage and row id.
     *
     * @param id      of the row
     * @param rawdata literal string, represent a {@link DataTable}
     */
    public void buildColumn(String id, String rawdata, int defaultValue) {
        DataTable dataset = new DataTable(rawdata);

        final List<String> sortedKeys = dataset.sortedKeys(new KeyComparator(true));
        if (buckets.isEmpty()) {
            for (int i = 0; i < sortedKeys.size(); i++) {
                final Bucket bucket = new Bucket();
                final String minValue = sortedKeys.get(i);

                if (Bucket.INFINITE_NEGATIVE.equals(minValue)) {
                    bucket.infiniteMin();
                } else {
                    bucket.setMin(Integer.parseInt(minValue));
                }

                if (i == sortedKeys.size() - 1) {
                    // last element
                    bucket.infiniteMax();
                } else {
                    final String max = sortedKeys.get(i + 1);
                    if (Bucket.INFINITE_POSITIVE.equals(max)) {
                        // If reach the infinite positive before the last element, ignore all other.
                        // Only for fail safe.
                        bucket.infiniteMax();
                        break;
                    } else {
                        bucket.setMax(Integer.parseInt(max));
                    }
                }
                this.addBucket(bucket);
            }
        }

        HeatMap.HeatMapColumn column = new HeatMap.HeatMapColumn();
        column.setId(id);
        sortedKeys.forEach(key -> {
            if (dataset.hasKey(key)) {
                column.addValue(dataset.get(key));
            } else {
                column.addValue((long) defaultValue);
            }
        });
        values.add(column);
    }

    public void fixMissingColumns(List<String> ids, int defaultValue) {
        for (int i = 0; i < ids.size(); i++) {
            final String expectedId = ids.get(i);
            boolean found = false;
            for (final HeatMapColumn value : values) {
                if (expectedId.equals(value.id)) {
                    found = true;
                }
            }
            if (!found) {
                final HeatMapColumn emptyColumn = buildMissingColumn(expectedId, defaultValue);
                values.add(i, emptyColumn);
            }
        }
    }

    private HeatMapColumn buildMissingColumn(String id, int defaultValue) {
        HeatMapColumn column = new HeatMapColumn();
        column.setId(id);
        buckets.forEach(bucket -> {
            column.addValue((long) defaultValue);
        });
        return column;
    }

    @Getter
    public static class HeatMapColumn {
        @Setter
        private String id;
        private List<Long> values = new ArrayList<>();

        public void addValue(Long value) {
            values.add(value);
        }
    }

    @RequiredArgsConstructor
    public static class KeyComparator implements Comparator<String> {
        private final boolean asc;

        @Override
        public int compare(final String k1, final String k2) {
            int result;
            String[] kk1 = parseKey(k1);
            String[] kk2 = parseKey(k2);
            result = kk1[0].compareTo(kk2[0]);
            if (result != 0) {
                return result;
            }
            final String key1 = kk1[1];
            final String key2 = kk2[1];
            if (key1.equals(key2)) {
                result = 0;
            } else if (Bucket.INFINITE_NEGATIVE.equals(key1) || Bucket.INFINITE_POSITIVE.equals(key2)) {
                result = -1;
            } else if (Bucket.INFINITE_NEGATIVE.equals(key2) || Bucket.INFINITE_POSITIVE.equals(key1)) {
                result = 1;
            } else {
                result = new BigInteger(key1).subtract(new BigInteger(key2)).signum();
            }

            return asc ? result : -result;
        }

        private String[] parseKey(String key) {
            if (key.contains(":")) {
                return key.split(":");
            }
            return new String[] {"default", key};
        }
    }
}
