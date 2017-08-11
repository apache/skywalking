package org.skywalking.apm.collector.agentjvm.worker.cpu.define;

import org.skywalking.apm.collector.core.framework.UnexpectedException;
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
public class CpuMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(CpuMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(CpuMetricTable.COLUMN_APPLICATION_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(CpuMetricTable.COLUMN_USAGE_PERCENT, AttributeType.DOUBLE, new CoverOperation()));
        addAttribute(3, new Attribute(CpuMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        throw new UnexpectedException("cpu metric data did not need send to remote worker.");
    }

    @Override public RemoteData serialize(Object object) {
        throw new UnexpectedException("cpu metric data did not need send to remote worker.");
    }

    public static class CpuMetric implements Transform<CpuMetric> {
        private String id;
        private int applicationInstanceId;
        private double usagePercent;
        private long timeBucket;

        public CpuMetric(String id, int applicationInstanceId, double usagePercent, long timeBucket) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.usagePercent = usagePercent;
            this.timeBucket = timeBucket;
        }

        public CpuMetric() {
        }

        @Override public Data toData() {
            CpuMetricDataDefine define = new CpuMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationInstanceId);
            data.setDataDouble(0, this.usagePercent);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public CpuMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
            this.usagePercent = data.getDataDouble(0);
            this.timeBucket = data.getDataLong(0);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationInstanceId(int applicationInstanceId) {
            this.applicationInstanceId = applicationInstanceId;
        }

        public void setUsagePercent(double usagePercent) {
            this.usagePercent = usagePercent;
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

        public double getUsagePercent() {
            return usagePercent;
        }

        public long getTimeBucket() {
            return timeBucket;
        }
    }
}
