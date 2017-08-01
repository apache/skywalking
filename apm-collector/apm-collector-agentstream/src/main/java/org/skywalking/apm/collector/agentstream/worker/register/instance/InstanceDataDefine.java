package org.skywalking.apm.collector.agentstream.worker.register.instance;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class InstanceDataDefine extends DataDefine {

    public static final int DEFINE_ID = 102;

    @Override public int defineId() {
        return DEFINE_ID;
    }

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute("id", AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(InstanceTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(InstanceTable.COLUMN_AGENTUUID, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(InstanceTable.COLUMN_REGISTER_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(InstanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(5, new Attribute(InstanceTable.COLUMN_HEARTBEAT_TIME, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        int applicationId = remoteData.getDataIntegers(0);
        String agentUUID = remoteData.getDataStrings(1);
        int instanceId = remoteData.getDataIntegers(1);
        long registerTime = remoteData.getDataLongs(0);
        return new Instance(id, applicationId, agentUUID, registerTime, instanceId);
    }

    @Override public RemoteData serialize(Object object) {
        Instance instance = (Instance)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(instance.getId());
        builder.addDataIntegers(instance.getApplicationId());
        builder.addDataStrings(instance.getAgentUUID());
        builder.addDataLongs(instance.getRegisterTime());
        return builder.build();
    }

    public static class Instance {
        private String id;
        private int applicationId;
        private String agentUUID;
        private long registerTime;
        private int instanceId;

        public Instance(String id, int applicationId, String agentUUID, long registerTime, int instanceId) {
            this.id = id;
            this.applicationId = applicationId;
            this.agentUUID = agentUUID;
            this.registerTime = registerTime;
            this.instanceId = instanceId;
        }

        public String getId() {
            return id;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public String getAgentUUID() {
            return agentUUID;
        }

        public long getRegisterTime() {
            return registerTime;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }

        public void setAgentUUID(String agentUUID) {
            this.agentUUID = agentUUID;
        }

        public void setRegisterTime(long registerTime) {
            this.registerTime = registerTime;
        }

        public void setInstanceId(int instanceId) {
            this.instanceId = instanceId;
        }
    }
}
