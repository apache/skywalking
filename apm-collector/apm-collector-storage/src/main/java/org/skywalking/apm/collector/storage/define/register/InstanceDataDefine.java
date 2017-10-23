/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.define.register;

import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author peng-yongsheng
 */
public class InstanceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 7;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(InstanceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(InstanceTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(InstanceTable.COLUMN_AGENT_UUID, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(InstanceTable.COLUMN_REGISTER_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(InstanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(5, new Attribute(InstanceTable.COLUMN_HEARTBEAT_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(InstanceTable.COLUMN_OS_INFO, AttributeType.STRING, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        int applicationId = remoteData.getDataIntegers(0);
        String agentUUID = remoteData.getDataStrings(1);
        int instanceId = remoteData.getDataIntegers(1);
        long registerTime = remoteData.getDataLongs(0);
        long heartBeatTime = remoteData.getDataLongs(1);
        String osInfo = remoteData.getDataStrings(2);
        return new Instance(id, applicationId, agentUUID, registerTime, instanceId, heartBeatTime, osInfo);
    }

    @Override public RemoteData serialize(Object object) {
        Instance instance = (Instance)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(instance.getId());
        builder.addDataIntegers(instance.getApplicationId());
        builder.addDataStrings(instance.getAgentUUID());
        builder.addDataIntegers(instance.getInstanceId());
        builder.addDataLongs(instance.getRegisterTime());
        builder.addDataLongs(instance.getHeartBeatTime());
        builder.addDataStrings(instance.getOsInfo());
        return builder.build();
    }

    public static class Instance {
        private String id;
        private int applicationId;
        private String agentUUID;
        private long registerTime;
        private int instanceId;
        private long heartBeatTime;
        private String osInfo;

        public Instance(String id, int applicationId, String agentUUID, long registerTime, int instanceId,
            long heartBeatTime,
            String osInfo) {
            this.id = id;
            this.applicationId = applicationId;
            this.agentUUID = agentUUID;
            this.registerTime = registerTime;
            this.instanceId = instanceId;
            this.heartBeatTime = heartBeatTime;
            this.osInfo = osInfo;
        }

        public Instance() {
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

        public long getHeartBeatTime() {
            return heartBeatTime;
        }

        public void setHeartBeatTime(long heartBeatTime) {
            this.heartBeatTime = heartBeatTime;
        }

        public String getOsInfo() {
            return osInfo;
        }

        public void setOsInfo(String osInfo) {
            this.osInfo = osInfo;
        }
    }
}
