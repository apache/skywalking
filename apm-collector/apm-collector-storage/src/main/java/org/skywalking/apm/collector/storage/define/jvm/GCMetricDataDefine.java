package org.skywalking.apm.collector.storage.define.jvm;

import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class GCMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 7;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(GCMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(GCMetricTable.COLUMN_PHRASE, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(GCMetricTable.COLUMN_COUNT, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(GCMetricTable.COLUMN_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(GCMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(GCMetricTable.COLUMN_5S_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        throw new UnexpectedException("gc metric data did not need send to remote worker.");
    }

    @Override public RemoteData serialize(Object object) {
        throw new UnexpectedException("gc metric data did not need send to remote worker.");
    }

    public static class GCMetric implements Transform<GCMetric> {
        private String id;
        private int applicationInstanceId;
        private int phrase;
        private long count;
        private long time;
        private long timeBucket;
        private long s5TimeBucket;

        public GCMetric(String id, int applicationInstanceId, int phrase, long count, long time, long timeBucket,
            long s5TimeBucket) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.phrase = phrase;
            this.count = count;
            this.time = time;
            this.timeBucket = timeBucket;
            this.s5TimeBucket = s5TimeBucket;
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
            data.setDataLong(3, this.s5TimeBucket);
            return data;
        }

        @Override public GCMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
            this.phrase = data.getDataInteger(1);
            this.count = data.getDataLong(0);
            this.time = data.getDataLong(1);
            this.timeBucket = data.getDataLong(2);
            this.s5TimeBucket = data.getDataLong(3);
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

        public long getS5TimeBucket() {
            return s5TimeBucket;
        }

        public void setS5TimeBucket(long s5TimeBucket) {
            this.s5TimeBucket = s5TimeBucket;
        }
    }
}
