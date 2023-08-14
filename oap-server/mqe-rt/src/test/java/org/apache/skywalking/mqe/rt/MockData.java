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

package org.apache.skywalking.mqe.rt;

import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.mqe.rt.type.Metadata;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;

public class MockData {
    public ExpressionResult newSeriesNoLabeledResult() {
        ExpressionResult seriesNoLabeled = new ExpressionResult();
        seriesNoLabeled.setType(ExpressionResultType.TIME_SERIES_VALUES);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue("100", 100));
        mqeValues.getValues().add(newMQEValue("300", 300));
        seriesNoLabeled.getResults().add(mqeValues);
        return seriesNoLabeled;
    }

    public ExpressionResult newSeriesNoLabeledResult(double id100, double id300) {
        ExpressionResult seriesNoLabeled = new ExpressionResult();
        seriesNoLabeled.setType(ExpressionResultType.TIME_SERIES_VALUES);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue("100", id100));
        mqeValues.getValues().add(newMQEValue("300", id300));
        seriesNoLabeled.getResults().add(mqeValues);
        return seriesNoLabeled;
    }

    public ExpressionResult newSeriesLabeledResult() {
        ExpressionResult seriesLabeled = new ExpressionResult();
        seriesLabeled.setLabeledResult(true);
        seriesLabeled.setType(ExpressionResultType.TIME_SERIES_VALUES);
        MQEValues mqeValues1 = new MQEValues();
        mqeValues1.setMetric(newMetadata("label", "1"));
        mqeValues1.getValues().add(newMQEValue("100", 100));
        mqeValues1.getValues().add(newMQEValue("300", 300));
        MQEValues mqeValues2 = new MQEValues();
        mqeValues2.setMetric(newMetadata("label", "2"));
        mqeValues2.getValues().add(newMQEValue("100", 101));
        mqeValues2.getValues().add(newMQEValue("300", 301));
        seriesLabeled.getResults().add(mqeValues1);
        seriesLabeled.getResults().add(mqeValues2);
        return seriesLabeled;
    }

    public ExpressionResult newSeriesLabeledResult(double id1001, double id3001, double id1002, double id3002) {
        ExpressionResult seriesLabeled = new ExpressionResult();
        seriesLabeled.setLabeledResult(true);
        seriesLabeled.setType(ExpressionResultType.TIME_SERIES_VALUES);
        MQEValues mqeValues1 = new MQEValues();
        mqeValues1.setMetric(newMetadata("label", "1"));
        mqeValues1.getValues().add(newMQEValue("100", id1001));
        mqeValues1.getValues().add(newMQEValue("300", id3001));
        MQEValues mqeValues2 = new MQEValues();
        mqeValues2.setMetric(newMetadata("label", "2"));
        mqeValues2.getValues().add(newMQEValue("100", id1002));
        mqeValues2.getValues().add(newMQEValue("300", id3002));
        seriesLabeled.getResults().add(mqeValues1);
        seriesLabeled.getResults().add(mqeValues2);
        return seriesLabeled;
    }

    public ExpressionResult newListResult() {
        ExpressionResult listResult = new ExpressionResult();
        listResult.setType(ExpressionResultType.SORTED_LIST);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue("service_A", 100));
        mqeValues.getValues().add(newMQEValue("service_B", 300));
        listResult.getResults().add(mqeValues);
        return listResult;
    }

    public ExpressionResult newListResult(double serviceA, double serviceB) {
        ExpressionResult listResult = new ExpressionResult();
        listResult.setType(ExpressionResultType.SORTED_LIST);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue("service_A", serviceA));
        mqeValues.getValues().add(newMQEValue("service_B", serviceB));
        listResult.getResults().add(mqeValues);
        return listResult;
    }

    public ExpressionResult newSingleResult(double value) {
        ExpressionResult listResult = new ExpressionResult();
        listResult.setType(ExpressionResultType.SINGLE_VALUE);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue(null, value));
        listResult.getResults().add(mqeValues);
        return listResult;
    }

    public ExpressionResult newSingleLabeledResult(double id1001, double id1002) {
        ExpressionResult result = new ExpressionResult();
        result.setLabeledResult(true);
        result.setType(ExpressionResultType.SINGLE_VALUE);
        MQEValues mqeValues1 = new MQEValues();
        mqeValues1.setMetric(newMetadata("label", "1"));
        mqeValues1.getValues().add(newMQEValue("100", id1001));
        MQEValues mqeValues2 = new MQEValues();
        mqeValues2.setMetric(newMetadata("label", "2"));
        mqeValues2.getValues().add(newMQEValue("100", id1002));
        result.getResults().add(mqeValues1);
        result.getResults().add(mqeValues2);
        return result;
    }

    public MQEValue newMQEValue(String id, double value) {
        MQEValue mqeValue = new MQEValue();
        mqeValue.setId(id);
        mqeValue.setDoubleValue(value);
        mqeValue.setEmptyValue(value == 0);
        return mqeValue;
    }

    public Metadata newMetadata(String key, String value) {
        Metadata metadata = new Metadata();
        metadata.getLabels().add(new KeyValue(key, value));
        return metadata;
    }
}
