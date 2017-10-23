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

package org.skywalking.apm.collector.storage.define.noderef;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.AddOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 11;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeReferenceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(3, new Attribute(NodeReferenceTable.COLUMN_BEHIND_PEER, AttributeType.STRING, new NonOperation()));
        addAttribute(4, new Attribute(NodeReferenceTable.COLUMN_S1_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(5, new Attribute(NodeReferenceTable.COLUMN_S3_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(6, new Attribute(NodeReferenceTable.COLUMN_S5_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(7, new Attribute(NodeReferenceTable.COLUMN_S5_GT, AttributeType.INTEGER, new AddOperation()));
        addAttribute(8, new Attribute(NodeReferenceTable.COLUMN_SUMMARY, AttributeType.INTEGER, new AddOperation()));
        addAttribute(9, new Attribute(NodeReferenceTable.COLUMN_ERROR, AttributeType.INTEGER, new AddOperation()));
        addAttribute(10, new Attribute(NodeReferenceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataString(1, remoteData.getDataStrings(1));
        data.setDataInteger(2, remoteData.getDataIntegers(2));
        data.setDataInteger(3, remoteData.getDataIntegers(3));
        data.setDataInteger(4, remoteData.getDataIntegers(4));
        data.setDataInteger(5, remoteData.getDataIntegers(5));
        data.setDataInteger(6, remoteData.getDataIntegers(6));
        data.setDataInteger(7, remoteData.getDataIntegers(7));
        data.setDataLong(0, remoteData.getDataLongs(0));
        return data;
    }

    @Override public RemoteData serialize(Object object) {
        Data data = (Data)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(data.getDataString(0));
        builder.addDataIntegers(data.getDataInteger(0));
        builder.addDataIntegers(data.getDataInteger(1));
        builder.addDataStrings(data.getDataString(1));
        builder.addDataIntegers(data.getDataInteger(2));
        builder.addDataIntegers(data.getDataInteger(3));
        builder.addDataIntegers(data.getDataInteger(4));
        builder.addDataIntegers(data.getDataInteger(5));
        builder.addDataIntegers(data.getDataInteger(6));
        builder.addDataIntegers(data.getDataInteger(7));
        builder.addDataLongs(data.getDataLong(0));
        return builder.build();
    }

    public static class NodeReference implements Transform {
        private String id;
        private int frontApplicationId;
        private int behindApplicationId;
        private String behindPeer;
        private int s1LTE = 0;
        private int s3LTE = 0;
        private int s5LTE = 0;
        private int s5GT = 0;
        private int summary = 0;
        private int error = 0;
        private long timeBucket;

        public NodeReference() {
        }

        @Override public Data toData() {
            NodeReferenceDataDefine define = new NodeReferenceDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.frontApplicationId);
            data.setDataInteger(1, this.behindApplicationId);
            data.setDataString(1, this.behindPeer);
            data.setDataInteger(2, this.s1LTE);
            data.setDataInteger(3, this.s3LTE);
            data.setDataInteger(4, this.s5LTE);
            data.setDataInteger(5, this.s5GT);
            data.setDataInteger(6, this.summary);
            data.setDataInteger(7, this.error);
            data.setDataLong(0, this.timeBucket);
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

        public int getFrontApplicationId() {
            return frontApplicationId;
        }

        public void setFrontApplicationId(int frontApplicationId) {
            this.frontApplicationId = frontApplicationId;
        }

        public int getBehindApplicationId() {
            return behindApplicationId;
        }

        public void setBehindApplicationId(int behindApplicationId) {
            this.behindApplicationId = behindApplicationId;
        }

        public String getBehindPeer() {
            return behindPeer;
        }

        public void setBehindPeer(String behindPeer) {
            this.behindPeer = behindPeer;
        }

        public int getS1LTE() {
            return s1LTE;
        }

        public void setS1LTE(int s1LTE) {
            this.s1LTE = s1LTE;
        }

        public int getS3LTE() {
            return s3LTE;
        }

        public void setS3LTE(int s3LTE) {
            this.s3LTE = s3LTE;
        }

        public int getS5LTE() {
            return s5LTE;
        }

        public void setS5LTE(int s5LTE) {
            this.s5LTE = s5LTE;
        }

        public int getS5GT() {
            return s5GT;
        }

        public void setS5GT(int s5GT) {
            this.s5GT = s5GT;
        }

        public int getError() {
            return error;
        }

        public void setError(int error) {
            this.error = error;
        }

        public int getSummary() {
            return summary;
        }

        public void setSummary(int summary) {
            this.summary = summary;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }
    }
}
