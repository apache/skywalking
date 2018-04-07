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

package org.apache.skywalking.apm.collector.instrument.tools;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * @author peng-yongsheng
 */
class MetricReader {

    private static final String AVG = "Avg";
    private static final String SUCCESS_RATE = "Success Rate";
    private static final String CALLS = "Calls";
    private static final String TOTAL = "Total";

    private final BufferedReader bufferedReader;

    MetricReader(BufferedReader bufferedReader) {
        this.bufferedReader = bufferedReader;
    }

    void read(Metric metric) throws IOException {
        String line = bufferedReader.readLine();

        String[] metrics = line.trim().split(",");
        for (String metricStr : metrics) {
            String[] keyValue = metricStr.split("=");

            String key = keyValue[0].trim();
            long value = getValue(keyValue[1]);
            switch (key) {
                case AVG:
                    metric.setAvg(value);
                    break;
                case SUCCESS_RATE:
                    metric.setRate(value);
                    break;
                case CALLS:
                    metric.setCalls(value);
                    break;
                case TOTAL:
                    metric.setTotal(value);
            }
        }
    }

    private long getValue(String valueStr) {
        char[] chars = valueStr.toCharArray();
        char[] value = new char[chars.length];

        int index = 0;
        for (char aChar : chars) {
            if (Character.isDigit(aChar)) {
                value[index] = aChar;
                index++;
            }
        }

        return Long.valueOf(String.valueOf(value).substring(0, index));
    }
}
