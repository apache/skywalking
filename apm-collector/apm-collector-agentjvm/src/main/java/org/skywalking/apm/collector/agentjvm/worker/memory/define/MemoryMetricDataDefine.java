package org.skywalking.apm.collector.agentjvm.worker.memory.define;

import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
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
public class MemoryMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 8;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(MemoryMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(MemoryMetricTable.COLUMN_APPLICATION_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(MemoryMetricTable.COLUMN_IS_HEAP, AttributeType.BOOLEAN, new CoverOperation()));
        addAttribute(3, new Attribute(MemoryMetricTable.COLUMN_INIT, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(MemoryMetricTable.COLUMN_MAX, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(MemoryMetricTable.COLUMN_USED, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(MemoryMetricTable.COLUMN_COMMITTED, AttributeType.LONG, new CoverOperation()));
        addAttribute(7, new Attribute(MemoryMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        throw new UnexpectedException("memory metric data did not need send to remote worker.");
    }

    @Override public RemoteData serialize(Object object) {
        throw new UnexpectedException("memory metric data did not need send to remote worker.");
    }

    public static class MemoryMetric implements Transform<MemoryMetric> {
        private String id;
        private int applicationInstanceId;
        private boolean isHeap;
        private long init;
        private long max;
        private long used;
        private long committed;
        private long timeBucket;

        public MemoryMetric(String id, int applicationInstanceId, boolean isHeap, long init, long max, long used,
            long committed, long timeBucket) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.isHeap = isHeap;
            this.init = init;
            this.max = max;
            this.used = used;
            this.committed = committed;
            this.timeBucket = timeBucket;
        }

        public MemoryMetric() {
        }

        @Override public Data toData() {
            MemoryMetricDataDefine define = new MemoryMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationInstanceId);
            data.setDataBoolean(0, this.isHeap);
            data.setDataLong(0, this.init);
            data.setDataLong(1, this.max);
            data.setDataLong(2, this.used);
            data.setDataLong(3, this.committed);
            data.setDataLong(4, this.timeBucket);
            return data;
        }

        @Override public MemoryMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
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
