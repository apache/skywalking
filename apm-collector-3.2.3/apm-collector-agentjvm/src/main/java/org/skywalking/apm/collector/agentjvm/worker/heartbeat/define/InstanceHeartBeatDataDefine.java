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

package org.skywalking.apm.collector.agentjvm.worker.heartbeat.define;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;

/**
 * @author peng-yongsheng
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
