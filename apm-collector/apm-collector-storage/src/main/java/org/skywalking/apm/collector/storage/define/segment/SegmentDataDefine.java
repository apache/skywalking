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
 * @author pengys5
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
