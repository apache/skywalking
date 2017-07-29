package org.skywalking.apm.collector.agentstream.worker.segment.define;

import com.google.protobuf.ByteString;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.TransformToData;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class SegmentDataDefine extends DataDefine {

    public static final int DEFINE_ID = 401;

    @Override public int defineId() {
        return DEFINE_ID;
    }

    @Override protected int initialCapacity() {
        return 2;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(SegmentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(SegmentTable.COLUMN_DATA_BINARY, AttributeType.BYTE, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        byte[] dataBinary = remoteData.getDataBytes(0).toByteArray();
        return new Segment(id, dataBinary);
    }

    @Override public RemoteData serialize(Object object) {
        Segment segment = (Segment)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(segment.getId());
        builder.addDataBytes(ByteString.copyFrom(segment.getDataBinary()));
        return builder.build();
    }

    public static class Segment implements TransformToData {
        private String id;
        private byte[] dataBinary;

        public Segment(String id, byte[] dataBinary) {
            this.id = id;
            this.dataBinary = dataBinary;
        }

        public Segment() {
        }

        @Override public Data transform() {
            SegmentDataDefine define = new SegmentDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataBytes(0, this.dataBinary);
            return data;
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
