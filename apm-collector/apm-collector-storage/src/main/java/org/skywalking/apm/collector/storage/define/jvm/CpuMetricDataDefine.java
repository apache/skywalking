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

package org.skywalking.apm.collector.storage.define.jvm;

import org.skywalking.apm.collector.core.framework.UnexpectedException;
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
public class CpuMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(CpuMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(CpuMetricTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(CpuMetricTable.COLUMN_USAGE_PERCENT, AttributeType.DOUBLE, new AddOperation()));
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
        private int instanceId;
        private double usagePercent;
        private long timeBucket;

        public CpuMetric(String id, int instanceId, double usagePercent, long timeBucket) {
            this.id = id;
            this.instanceId = instanceId;
            this.usagePercent = usagePercent;
            this.timeBucket = timeBucket;
        }

        public CpuMetric() {
        }

        @Override public Data toData() {
            CpuMetricDataDefine define = new CpuMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.instanceId);
            data.setDataDouble(0, this.usagePercent);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public CpuMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.instanceId = data.getDataInteger(0);
            this.usagePercent = data.getDataDouble(0);
            this.timeBucket = data.getDataLong(0);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setInstanceId(int instanceId) {
            this.instanceId = instanceId;
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

        public int getInstanceId() {
            return instanceId;
        }

        public double getUsagePercent() {
            return usagePercent;
        }

        public long getTimeBucket() {
            return timeBucket;
        }
    }
}
