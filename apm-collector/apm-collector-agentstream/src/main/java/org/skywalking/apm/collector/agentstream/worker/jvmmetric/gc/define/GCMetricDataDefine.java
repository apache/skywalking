package org.skywalking.apm.collector.agentstream.worker.jvmmetric.gc.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.Transform;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class GCMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(GCMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(GCMetricTable.COLUMN_PHRASE, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(GCMetricTable.COLUMN_COUNT, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(GCMetricTable.COLUMN_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(GCMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class GCMetric implements Transform<GCMetric> {
        private String id;
        private int applicationInstanceId;
        private int phrase;
        private long count;
        private long time;
        private long timeBucket;

        public GCMetric(String id, int applicationInstanceId, int phrase, long count, long time, long timeBucket) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.phrase = phrase;
            this.count = count;
            this.time = time;
            this.timeBucket = timeBucket;
        }

        public GCMetric() {
        }

        @Override public Data toData() {
            GCMetricDataDefine define = new GCMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationInstanceId);
            data.setDataInteger(1, this.phrase);
            data.setDataLong(0, this.count);
            data.setDataLong(1, this.time);
            data.setDataLong(2, this.timeBucket);
            return data;
        }

        @Override public GCMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
            this.phrase = data.getDataInteger(1);
            this.count = data.getDataLong(0);
            this.time = data.getDataLong(1);
            this.timeBucket = data.getDataLong(2);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationInstanceId(int applicationInstanceId) {
            this.applicationInstanceId = applicationInstanceId;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }

        public String getId() {
            return id;
        }

        public int getApplicationInstanceId() {
            return applicationInstanceId;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public int getPhrase() {
            return phrase;
        }

        public long getCount() {
            return count;
        }

        public long getTime() {
            return time;
        }

        public void setPhrase(int phrase) {
            this.phrase = phrase;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public void setTime(long time) {
            this.time = time;
        }
    }
}
