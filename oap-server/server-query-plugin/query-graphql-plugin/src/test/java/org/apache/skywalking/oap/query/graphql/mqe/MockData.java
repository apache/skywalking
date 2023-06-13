package org.apache.skywalking.oap.query.graphql.mqe;

import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValue;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValues;
import org.apache.skywalking.oap.query.graphql.type.mql.Metadata;
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

    public ExpressionResult newSingleResult() {
        ExpressionResult listResult = new ExpressionResult();
        listResult.setType(ExpressionResultType.SINGLE_VALUE);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(newMQEValue(null, 1000));
        listResult.getResults().add(mqeValues);
        return listResult;
    }

    public MQEValue newMQEValue(String id, double value) {
        MQEValue mqeValue = new MQEValue();
        mqeValue.setId(id);
        mqeValue.setDoubleValue(value);
        return mqeValue;
    }

    public Metadata newMetadata(String key, String value) {
        Metadata metadata = new Metadata();
        metadata.getLabels().add(new KeyValue(key, value));
        return metadata;
    }
}
