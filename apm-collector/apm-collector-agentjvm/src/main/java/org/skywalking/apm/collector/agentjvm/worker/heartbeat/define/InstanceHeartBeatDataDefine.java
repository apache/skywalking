package org.skywalking.apm.collector.agentjvm.worker.heartbeat.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.table.register.InstanceTable;
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
public class InstanceHeartBeatDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(InstanceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(InstanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(InstanceTable.COLUMN_HEARTBEAT_TIME, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        int instanceId = remoteData.getDataIntegers(0);
        long heartBeatTime = remoteData.getDataLongs(0);
        return new InstanceHeartBeat(id, heartBeatTime, instanceId);
    }

    @Override public RemoteData serialize(Object object) {
        InstanceHeartBeat instanceHeartBeat = (InstanceHeartBeat)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(instanceHeartBeat.getId());
        builder.addDataIntegers(instanceHeartBeat.getInstanceId());
        builder.addDataLongs(instanceHeartBeat.getHeartBeatTime());
        return builder.build();
    }

    public static class InstanceHeartBeat implements Transform<InstanceHeartBeat> {
        private String id;
        private long heartBeatTime;
        private int instanceId;

        public InstanceHeartBeat(String id, long heartBeatTime, int instanceId) {
            this.id = id;
            this.heartBeatTime = heartBeatTime;
            this.instanceId = instanceId;
        }

        public InstanceHeartBeat() {
        }

        @Override public Data toData() {
            InstanceHeartBeatDataDefine define = new InstanceHeartBeatDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.instanceId);
            data.setDataLong(0, this.heartBeatTime);
            return data;
        }

        @Override public InstanceHeartBeat toSelf(Data data) {
            this.id = data.getDataString(0);
            this.instanceId = data.getDataInteger(0);
            this.heartBeatTime = data.getDataLong(0);
            return this;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getHeartBeatTime() {
            return heartBeatTime;
        }

        public void setHeartBeatTime(long heartBeatTime) {
            this.heartBeatTime = heartBeatTime;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(int instanceId) {
            this.instanceId = instanceId;
        }
    }
}
