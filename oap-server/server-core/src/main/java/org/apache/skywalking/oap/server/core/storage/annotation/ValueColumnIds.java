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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.util.*;
import org.apache.skywalking.oap.server.core.query.sql.Function;

/**
 * @author peng-yongsheng
 */
public enum ValueColumnIds {
    INSTANCE;

    private Map<String, ValueColumn> mapping = new HashMap<>();

    public void putIfAbsent(String indName, String valueCName, Function function) {
        mapping.putIfAbsent(indName, new ValueColumn(valueCName, function));
    }

    public String getValueCName(String metricsName) {
        return findColumn(metricsName).valueCName;
    }

    public Function getValueFunction(String metricsName) {
        return findColumn(metricsName).function;
    }

    private ValueColumn findColumn(String metricsName) {
        ValueColumn column = mapping.get(metricsName);
        if (column == null) {
            throw new RuntimeException("Metrics:" + metricsName + " doesn't have value column definition");
        }
        return column;
    }

    class ValueColumn {
        private final String valueCName;
        private final Function function;

        private ValueColumn(String valueCName, Function function) {
            this.valueCName = valueCName;
            this.function = function;
        }
    }
}
