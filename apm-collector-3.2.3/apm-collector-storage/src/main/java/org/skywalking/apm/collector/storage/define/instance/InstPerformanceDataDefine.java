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
 * @author peng-yongsheng
 */
public class InstPerformanceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(InstPerformanceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(InstPerformanceTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(InstPerformanceTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(InstPerformanceTable.COLUMN_CALLS, AttributeType.INTEGER, new AddOperation()));
        addAttribute(4, new Attribute(InstPerformanceTable.COLUMN_COST_TOTAL, AttributeType.LONG, new AddOperation()));
        addAttribute(5, new Attribute(InstPerformanceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataInteger(2, remoteData.getDataIntegers(2));
        data.setDataLong(0, remoteData.getDataLongs(0));
        data.setDataLong(1, remoteData.getDataLongs(1));
        return data;
    }

    @Override public RemoteData serialize(Object object) {
        Data data = (Data)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(data.getDataString(0));
        builder.addDataIntegers(data.getDataInteger(0));
        builder.addDataIntegers(data.getDataInteger(1));
        builder.addDataIntegers(data.getDataInteger(2));
        builder.addDataLongs(data.getDataLong(0));
        builder.addDataLongs(data.getDataLong(1));
        return builder.build();
    }

    public static class InstPerformance implements Transform<InstPerformance> {
        private String id;
        private int applicationId;
        private int instanceId;
        private int calls;
        private long costTotal;
        private long timeBucket;

        public InstPerformance() {
        }

        @Override public Data toData() {
            InstPerformanceDataDefine define = new InstPerformanceDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataInteger(1, this.instanceId);
            data.setDataInteger(2, this.calls);
            data.setDataLong(0, this.costTotal);
            data.setDataLong(1, this.timeBucket);
            return data;
        }

        @Override public InstPerformance toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.instanceId = data.getDataInteger(1);
            this.calls = data.getDataInteger(2);
            this.costTotal = data.getDataLong(0);
            this.timeBucket = data.getDataLong(1);
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

        public int getCalls() {
            return calls;
        }

        public void setCalls(int calls) {
            this.calls = calls;
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
    }
}
