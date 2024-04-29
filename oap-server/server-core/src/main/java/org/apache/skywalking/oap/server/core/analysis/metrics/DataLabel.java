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
 */

package org.apache.skywalking.oap.server.core.analysis.metrics;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@EqualsAndHashCode(callSuper = true)
public class DataLabel extends LinkedHashMap<String/*labelKey*/, String/*labelValue*/> {
    public final static String GENERAL_LABEL_NAME = "_";
    public final static String PERCENTILE_LABEL_NAME = "p";

    /**
     * Parse the labels String to a map.
     *
     * @param labels the String format is {key1=value1,key2=value1}, if not match the format will use `_` as the key and
     *               the `labels string` as the value.
     */
    public void put(String labels) {
        if (labels.startsWith(Const.LEFT_BRACE) && labels.endsWith(Const.RIGHT_BRACE)) {
            String labelsStr = labels.substring(1, labels.length() - 1);
            if (StringUtil.isNotEmpty(labelsStr)) {
                String[] labelArr = labelsStr.split(Const.COMMA);
                put(labelArr);
            }
        } else {
            //set `_` as the key and the `labels string` as the value.
            put(GENERAL_LABEL_NAME, labels);
        }
    }

    private void put(String[] labels) {
        for (String label : labels) {
            int i = label.indexOf(Const.EQUAL);
            if (i > 0) {
                String key = label.substring(0, i);
                String value = label.substring(i + 1);
                put(key, value);
            }
        }
    }

    public String getLabelString(String key) {
        return key + Const.EQUAL + get(key);
    }

    @Override
    public String put(String key, String value) {
        return super.put(key, value);
    }

    @Override
    public String toString() {
        Iterator<Map.Entry<String, String>> i = entrySet().iterator();
        if (!i.hasNext()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (; ; ) {
            Map.Entry<String, String> e = i.next();
            String key = e.getKey();
            String value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',');
        }
    }
}
