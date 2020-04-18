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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * Query metrics values in different ways.
 *
 * @since 8.0.0
 */
public interface IMetricsQueryDAO extends DAO {
    int readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException;

    MetricsValues readMetricsValues(MetricsCondition condition,
                                    String valueColumnName,
                                    Duration duration) throws IOException;

    List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                 String valueColumnName,
                                                 List<String> labels,
                                                 Duration duration) throws IOException;

    HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException;

    class Util {
        /**
         * Make sure the order is same as the expected order, add defaultValue if absent.
         */
        public static IntValues sortValues(IntValues origin, List<String> expectedOrder, int defaultValue) {
            IntValues intValues = new IntValues();

            expectedOrder.forEach(id -> {
                KVInt e = new KVInt();
                e.setId(id);
                e.setValue(origin.findValue(id, defaultValue));
                intValues.addKVInt(e);
            });

            return intValues;
        }

        /**
         * Make sure the order is same as the expected order, add defaultValue if absent.
         */
        public static List<MetricsValues> sortValues(List<MetricsValues> origin,
                                                     List<String> expectedOrder,
                                                     int defaultValue) {
            for (int i = 0; i < origin.size(); i++) {
                final MetricsValues metricsValues = origin.get(i);
                metricsValues.setValues(sortValues(metricsValues.getValues(), expectedOrder, defaultValue));
            }
            return origin;
        }
    }
}
