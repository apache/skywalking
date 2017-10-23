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
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author peng-yongsheng
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
