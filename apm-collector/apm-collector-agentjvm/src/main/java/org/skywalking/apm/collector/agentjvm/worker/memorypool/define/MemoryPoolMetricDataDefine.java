package org.skywalking.apm.collector.agentjvm.worker.memorypool.define;

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
public class MemoryPoolMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 9;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(MemoryPoolMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(MemoryPoolMetricTable.COLUMN_APPLICATION_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(MemoryPoolMetricTable.COLUMN_POOL_TYPE, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(MemoryPoolMetricTable.COLUMN_IS_HEAP, AttributeType.BOOLEAN, new CoverOperation()));
        addAttribute(4, new Attribute(MemoryPoolMetricTable.COLUMN_INIT, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(MemoryPoolMetricTable.COLUMN_MAX, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(MemoryPoolMetricTable.COLUMN_USED, AttributeType.LONG, new CoverOperation()));
        addAttribute(7, new Attribute(MemoryPoolMetricTable.COLUMN_COMMITTED, AttributeType.LONG, new CoverOperation()));
        addAttribute(8, new Attribute(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class MemoryPoolMetric implements Transform<MemoryPoolMetric> {
        private String id;
        private int applicationInstanceId;
        private int poolType;
        private boolean isHeap;
        private long init;
        private long max;
        private long used;
        private long committed;
        private long timeBucket;

        public MemoryPoolMetric(String id, int applicationInstanceId, int poolType, boolean isHeap, long init, long max,
            long used, long committed, long timeBucket) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.poolType = poolType;
            this.isHeap = isHeap;
            this.init = init;
            this.max = max;
            this.used = used;
            this.committed = committed;
            this.timeBucket = timeBucket;
        }

        public MemoryPoolMetric() {
        }

        @Override public Data toData() {
            MemoryPoolMetricDataDefine define = new MemoryPoolMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationInstanceId);
            data.setDataInteger(1, this.poolType);
            data.setDataBoolean(0, this.isHeap);
            data.setDataLong(0, this.init);
            data.setDataLong(1, this.max);
            data.setDataLong(2, this.used);
            data.setDataLong(3, this.committed);
            data.setDataLong(4, this.timeBucket);
            return data;
        }

        @Override public MemoryPoolMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
            this.poolType = data.getDataInteger(1);
            this.isHeap = data.getDataBoolean(0);
            this.init = data.getDataLong(0);
            this.max = data.getDataLong(1);
            this.used = data.getDataLong(2);
            this.committed = data.getDataLong(3);
            this.timeBucket = data.getDataLong(4);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationInstanceId(int applicationInstanceId) {
            this.applicationInstanceId = applicationInstanceId;
        }

        public void setPoolType(int poolType) {
            this.poolType = poolType;
        }

        public void setHeap(boolean heap) {
            isHeap = heap;
        }

        public void setInit(long init) {
            this.init = init;
        }

        public void setMax(long max) {
            this.max = max;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public void setCommitted(long committed) {
            this.committed = committed;
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
    }
}
