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
public class SegmentDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 2;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(SegmentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(SegmentTable.COLUMN_DATA_BINARY, AttributeType.BYTE, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class Segment implements Transform {
        private String id;
        private byte[] dataBinary;

        public Segment() {
        }

        @Override public Data toData() {
            SegmentDataDefine define = new SegmentDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataBytes(0, this.dataBinary);
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

        public byte[] getDataBinary() {
            return dataBinary;
        }

        public void setDataBinary(byte[] dataBinary) {
            this.dataBinary = dataBinary;
        }
    }
}
