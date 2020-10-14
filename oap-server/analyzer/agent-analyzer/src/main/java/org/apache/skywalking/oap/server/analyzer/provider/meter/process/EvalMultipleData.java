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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import io.vavr.Function2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Combined meter data, has multiple meter data. Support batch process the express on meter
 */
public class EvalMultipleData extends EvalData<EvalMultipleData> {

    /**
     * Multiple eval data
     */
    private final List<EvalData> dataList;

    public EvalMultipleData(String meterName) {
        this(meterName, new ArrayList<>());
    }

    public EvalMultipleData(String meterName, List<EvalData> dataList) {
        this.name = meterName;
        this.dataList = dataList;
    }

    /**
     * Filter the same tag key and value
     */
    public EvalMultipleData tagFilter(String key, String value) {
        // Wrapper a new eval data
        return createNew(dataList.stream().filter(m -> m.hasSameTag(key, value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData multiply(double value) {
        return createNew(dataList.stream().map(m -> m.multiply(value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData multiply(EvalMultipleData value) {
        return calculateWithSingleValue(value, "multiply", EvalData::multiply);
    }

    @Override
    public EvalMultipleData add(double value) {
        return createNew(dataList.stream().map(m -> m.add(value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData add(EvalMultipleData value) {
        return calculateWithSingleValue(value, "add", EvalData::add);
    }

    @Override
    public EvalMultipleData scale(Integer value) {
        return createNew(dataList.stream().map(m -> m.scale(value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData minus(double value) {
        return createNew(dataList.stream().map(m -> m.minus(value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData minus(EvalMultipleData value) {
        return calculateWithSingleValue(value, "reduce", EvalData::minus);
    }

    @Override
    public EvalMultipleData divide(double value) {
        return createNew(dataList.stream().map(m -> m.divide(value)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData divide(EvalMultipleData value) {
        return calculateWithSingleValue(value, "mean", EvalData::divide);
    }

    @Override
    public EvalMultipleData irate(String range) {
        return createNew(dataList.stream().map(m -> m.irate(range)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData rate(String range) {
        return createNew(dataList.stream().map(m -> m.rate(range)).collect(Collectors.toList()));
    }

    @Override
    public EvalMultipleData increase(String range) {
        return createNew(dataList.stream().map(m -> m.increase(range)).collect(Collectors.toList()));
    }

    private EvalMultipleData createNew(List<EvalData> dataList) {
        return new EvalMultipleData(name, dataList);
    }

    /**
     * Combine all of the meter as single value
     */
    EvalData combineAsSingleData() {
        return dataList.stream().reduce(EvalData::combine).orElseThrow(IllegalArgumentException::new);
    }

    /**
     * Combine all of the meter and group by labeled names
     * @return Same labeled names and values
     */
    Map<String, EvalData> combineAndGroupBy(List<String> labelNames) {
        return dataList.stream()
            // group by label
            .collect(groupingBy(m -> labelNames.stream().map(l -> m.getLabels().getOrDefault(l, "")).map(Objects::toString).collect(Collectors.joining("-"))))
            .entrySet().stream().collect(
                // combine labeled values
                Collectors.toMap(
                    e -> e.getKey(),
                    e -> e.getValue().stream().reduce(EvalData::combine).orElse(null)));
    }

    /**
     * Append data to ready eval list
     */
    void appendData(EvalData data) {
        dataList.add(data);
    }

    @Override
    EvalMultipleData combine(EvalMultipleData data) {
        throw new UnsupportedOperationException();
    }

    /**
     * The meter list only support single data
     */
    private EvalMultipleData calculateWithSingleValue(EvalMultipleData value, String functionName, Function2<EvalData, EvalData, EvalData> invoke) {
        if (value.dataList.size() > 1) {
            // Only support calculate with single value
            throw new IllegalArgumentException("Unsupported data size with calculator " + functionName + ", please filter as a single value: " + value.name);
        } else if (value.dataList.size() == 0) {
            // If calculate with empty data, just return self
            return this;
        } else {
            final EvalData calculateWith = value.dataList.get(0);
            return createNew(dataList.stream().map(c -> invoke.apply(c, calculateWith)).collect(Collectors.toList()));
        }
    }
}
