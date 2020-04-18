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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
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
    private List<Bucket> buckets = new ArrayList<>(10);

    public void addBucket(Bucket bucket) {
        this.buckets.add(bucket);
    }

    /**
     * Build one heatmap value column based on rawdata in the storage and row id.
     *
     * @param id      of the row
     * @param rawdata literal string, represent a {@link DataTable}
     */
    public void buildColumn(String id, String rawdata) {
        DataTable dataset = new DataTable(rawdata);

        final List<String> sortedKeys = dataset.sortedKeys(
            Comparator.comparingInt(Integer::parseInt));
        if (buckets == null) {
            buckets = new ArrayList<>(dataset.size());
            for (int i = 0; i < sortedKeys.size(); i++) {
                if (i == 0) {
                    this.addBucket(new Bucket(0, Integer.parseInt(sortedKeys.get(i))));
                } else {
                    this.addBucket(new Bucket(
                        Integer.parseInt(sortedKeys.get(i - 1)),
                        Integer.parseInt(sortedKeys.get(i))
                    ));
                }
            }
        }

        HeatMap.HeatMapColumn column = new HeatMap.HeatMapColumn();
        column.setId(id);
        sortedKeys.forEach(key -> {
            column.addValue(dataset.get(key));

        });
    }

    public void fixMissingColumns(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            final String expectedId = ids.get(i);
            final HeatMapColumn column = values.get(i);
            if (expectedId.equals(column.id)) {
                continue;
            } else {
                final HeatMapColumn emptyColumn = buildMissingColumn(expectedId);
                values.add(i, emptyColumn);
            }
        }
    }

    private HeatMapColumn buildMissingColumn(String id) {
        HeatMapColumn column = new HeatMapColumn();
        column.setId(id);
        buckets.forEach(bucket -> {
            column.addValue(0L);
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
}
