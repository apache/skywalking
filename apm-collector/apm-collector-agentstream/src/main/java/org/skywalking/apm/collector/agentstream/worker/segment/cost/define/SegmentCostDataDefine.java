package org.skywalking.apm.collector.agentstream.worker.segment.cost.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.TransformToData;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class SegmentCostDataDefine extends DataDefine {

    public static final int DEFINE_ID = 402;

    @Override public int defineId() {
        return DEFINE_ID;
    }

    @Override protected int initialCapacity() {
        return 7;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(SegmentCostTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(SegmentCostTable.COLUMN_SEGMENT_ID, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(SegmentCostTable.COLUMN_GLOBAL_TRACE_ID, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(SegmentCostTable.COLUMN_OPERATION_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(4, new Attribute(SegmentCostTable.COLUMN_COST, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(SegmentCostTable.COLUMN_START_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(SegmentCostTable.COLUMN_END_TIME, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String segmentId = remoteData.getDataStrings(1);
        String globalTraceId = remoteData.getDataStrings(2);
        String operationName = remoteData.getDataStrings(3);
        Long cost = remoteData.getDataLongs(0);
        Long startTime = remoteData.getDataLongs(1);
        Long endTime = remoteData.getDataLongs(2);
        return new SegmentCost(id, segmentId, globalTraceId, operationName, cost, startTime, endTime);
    }

    @Override public RemoteData serialize(Object object) {
        SegmentCost segmentCost = (SegmentCost)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(segmentCost.getId());
        builder.addDataStrings(segmentCost.getSegmentId());
        builder.addDataStrings(segmentCost.getGlobalTraceId());
        builder.addDataStrings(segmentCost.getOperationName());
        builder.addDataLongs(segmentCost.getCost());
        builder.addDataLongs(segmentCost.getStartTime());
        builder.addDataLongs(segmentCost.getEndTime());
        return builder.build();
    }

    public static class SegmentCost implements TransformToData {
        private String id;
        private String segmentId;
        private String globalTraceId;
        private String operationName;
        private Long cost;
        private Long startTime;
        private Long endTime;

        public SegmentCost(String id, String segmentId, String globalTraceId, String operationName, Long cost,
            Long startTime, Long endTime) {
            this.id = id;
            this.segmentId = segmentId;
            this.globalTraceId = globalTraceId;
            this.operationName = operationName;
            this.cost = cost;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public SegmentCost() {
        }

        @Override public Data transform() {
            SegmentCostDataDefine define = new SegmentCostDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.segmentId);
            data.setDataString(2, this.globalTraceId);
            data.setDataString(3, this.operationName);
            data.setDataLong(0, this.cost);
            data.setDataLong(1, this.startTime);
            data.setDataLong(2, this.endTime);
            return data;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSegmentId() {
            return segmentId;
        }

        public void setSegmentId(String segmentId) {
            this.segmentId = segmentId;
        }

        public String getGlobalTraceId() {
            return globalTraceId;
        }

        public void setGlobalTraceId(String globalTraceId) {
            this.globalTraceId = globalTraceId;
        }

        public String getOperationName() {
            return operationName;
        }

        public void setOperationName(String operationName) {
            this.operationName = operationName;
        }

        public Long getCost() {
            return cost;
        }

        public void setCost(Long cost) {
            this.cost = cost;
        }

        public Long getStartTime() {
            return startTime;
        }

        public void setStartTime(Long startTime) {
            this.startTime = startTime;
        }

        public Long getEndTime() {
            return endTime;
        }

        public void setEndTime(Long endTime) {
            this.endTime = endTime;
        }
    }
}
