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

package org.skywalking.apm.collector.storage.define.segment;

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
public class SegmentCostDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 9;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(SegmentCostTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(SegmentCostTable.COLUMN_SEGMENT_ID, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(SegmentCostTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(SegmentCostTable.COLUMN_SERVICE_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(4, new Attribute(SegmentCostTable.COLUMN_COST, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(SegmentCostTable.COLUMN_START_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(SegmentCostTable.COLUMN_END_TIME, AttributeType.LONG, new CoverOperation()));
        addAttribute(7, new Attribute(SegmentCostTable.COLUMN_IS_ERROR, AttributeType.BOOLEAN, new CoverOperation()));
        addAttribute(8, new Attribute(SegmentCostTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class SegmentCost implements Transform {
        private String id;
        private int applicationId;
        private String segmentId;
        private String serviceName;
        private Long cost;
        private Long startTime;
        private Long endTime;
        private boolean isError;
        private long timeBucket;

        public SegmentCost() {
        }

        @Override public Data toData() {
            SegmentCostDataDefine define = new SegmentCostDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.segmentId);
            data.setDataString(2, this.serviceName);
            data.setDataInteger(0, this.applicationId);
            data.setDataLong(0, this.cost);
            data.setDataLong(1, this.startTime);
            data.setDataLong(2, this.endTime);
            data.setDataBoolean(0, this.isError);
            data.setDataLong(3, this.timeBucket);
            return data;
        }

        @Override public Object toSelf(Data data) {
            return null;
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

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
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

        public boolean isError() {
            return isError;
        }

        public void setError(boolean error) {
            isError = error;
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
