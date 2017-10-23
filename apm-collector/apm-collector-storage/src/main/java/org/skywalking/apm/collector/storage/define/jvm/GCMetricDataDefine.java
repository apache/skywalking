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
public class GCMetricDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(GCMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(GCMetricTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(GCMetricTable.COLUMN_PHRASE, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(GCMetricTable.COLUMN_COUNT, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(GCMetricTable.COLUMN_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(GCMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        throw new UnexpectedException("gc metric data did not need send to remote worker.");
    }

    @Override public RemoteData serialize(Object object) {
        throw new UnexpectedException("gc metric data did not need send to remote worker.");
    }

    public static class GCMetric implements Transform<GCMetric> {
        private String id;
        private int instanceId;
        private int phrase;
        private long count;
        private long time;
        private long timeBucket;

        public GCMetric(String id, int instanceId, int phrase, long count, long time, long timeBucket) {
            this.id = id;
            this.instanceId = instanceId;
            this.phrase = phrase;
            this.count = count;
            this.time = time;
            this.timeBucket = timeBucket;
        }

        public GCMetric() {
        }

        @Override public Data toData() {
            GCMetricDataDefine define = new GCMetricDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.instanceId);
            data.setDataInteger(1, this.phrase);
            data.setDataLong(0, this.count);
            data.setDataLong(1, this.time);
            data.setDataLong(2, this.timeBucket);
            return data;
        }

        @Override public GCMetric toSelf(Data data) {
            this.id = data.getDataString(0);
            this.instanceId = data.getDataInteger(0);
            this.phrase = data.getDataInteger(1);
            this.count = data.getDataLong(0);
            this.time = data.getDataLong(1);
            this.timeBucket = data.getDataLong(2);
            return this;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setInstanceId(int instanceId) {
            this.instanceId = instanceId;
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

        public long getTimeBucket() {
            return timeBucket;
        }

        public int getPhrase() {
            return phrase;
        }

        public long getCount() {
            return count;
        }

        public long getTime() {
            return time;
        }

        public void setPhrase(int phrase) {
            this.phrase = phrase;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public void setTime(long time) {
            this.time = time;
        }
    }
}
