package org.skywalking.apm.collector.storage.define.instance;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.AddOperation;
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class InstPerformanceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 7;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(InstPerformanceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(InstPerformanceTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(InstPerformanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(InstPerformanceTable.COLUMN_CALL_TIMES, AttributeType.INTEGER, new AddOperation()));
        addAttribute(4, new Attribute(InstPerformanceTable.COLUMN_COST_TOTAL, AttributeType.LONG, new AddOperation()));
        addAttribute(5, new Attribute(InstPerformanceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(InstPerformanceTable.COLUMN_5S_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class InstPerformance implements Transform<InstPerformance> {
        private String id;
        private int applicationId;
        private int instanceId;
        private int callTimes;
        private long costTotal;
        private long timeBucket;
        private long s5TimeBucket;

        public InstPerformance(String id, int applicationId, int instanceId, int callTimes, long costTotal,
            long timeBucket,
            long s5TimeBucket) {
            this.id = id;
            this.applicationId = applicationId;
            this.instanceId = instanceId;
            this.callTimes = callTimes;
            this.costTotal = costTotal;
            this.timeBucket = timeBucket;
            this.s5TimeBucket = s5TimeBucket;
        }

        public InstPerformance() {
        }

        @Override public Data toData() {
            InstPerformanceDataDefine define = new InstPerformanceDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataInteger(1, this.instanceId);
            data.setDataInteger(2, this.callTimes);
            data.setDataLong(0, this.costTotal);
            data.setDataLong(1, this.timeBucket);
            data.setDataLong(2, this.s5TimeBucket);
            return data;
        }

        @Override public InstPerformance toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.instanceId = data.getDataInteger(1);
            this.callTimes = data.getDataInteger(2);
            this.costTotal = data.getDataLong(0);
            this.timeBucket = data.getDataLong(1);
            this.s5TimeBucket = data.getDataLong(2);
            return this;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(int instanceId) {
            this.instanceId = instanceId;
        }

        public int getCallTimes() {
            return callTimes;
        }

        public void setCallTimes(int callTimes) {
            this.callTimes = callTimes;
        }

        public long getCostTotal() {
            return costTotal;
        }

        public void setCostTotal(long costTotal) {
            this.costTotal = costTotal;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }

        public long getS5TimeBucket() {
            return s5TimeBucket;
        }

        public void setS5TimeBucket(long s5TimeBucket) {
            this.s5TimeBucket = s5TimeBucket;
        }
    }
}
