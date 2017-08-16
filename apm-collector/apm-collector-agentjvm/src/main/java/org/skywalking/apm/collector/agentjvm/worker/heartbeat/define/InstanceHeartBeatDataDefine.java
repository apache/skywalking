package org.skywalking.apm.collector.agentjvm.worker.heartbeat.define;

import org.skywalking.apm.collector.core.framework.UnexpectedException;
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
        addAttribute(1, new Attribute(InstanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(InstanceTable.COLUMN_HEARTBEAT_TIME, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        throw new UnexpectedException("instance heart beat data did not need send to remote worker.");
    }

    @Override public RemoteData serialize(Object object) {
        throw new UnexpectedException("instance heart beat data did not need send to remote worker.");
    }

    public static class InstanceHeartBeat implements Transform<InstanceHeartBeat> {
        private String id;
        private int applicationInstanceId;
        private long heartbeatTime;

        public InstanceHeartBeat(String id, int applicationInstanceId, long heartbeatTime) {
            this.id = id;
            this.applicationInstanceId = applicationInstanceId;
            this.heartbeatTime = heartbeatTime;
        }

        public InstanceHeartBeat() {
        }

        @Override public Data toData() {
            InstanceHeartBeatDataDefine define = new InstanceHeartBeatDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationInstanceId);
            data.setDataLong(0, this.heartbeatTime);
            return data;
        }

        @Override public InstanceHeartBeat toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationInstanceId = data.getDataInteger(0);
            this.heartbeatTime = data.getDataLong(0);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationInstanceId(int applicationInstanceId) {
            this.applicationInstanceId = applicationInstanceId;
        }

        public String getId() {
            return id;
        }

        public int getApplicationInstanceId() {
            return applicationInstanceId;
        }

        public long getHeartbeatTime() {
            return heartbeatTime;
        }

        public void setHeartbeatTime(long heartbeatTime) {
            this.heartbeatTime = heartbeatTime;
        }
    }
}
