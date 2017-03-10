package com.a.eye.skywalking.collector.worker;

import akka.serialization.JSerializer;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.proto.SegmentMessage;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * @author pengys5
 */
public class TraceSegmentSerializer extends JSerializer {
    @Override
    public boolean includeManifest() {
        return false;
    }

    @Override
    public int identifier() {
        return 0;
    }

    @Override
    public byte[] toBinary(Object o) {
//        System.out.println("toBinary");
        TraceSegment traceSegment = (TraceSegment) o;
        return traceSegment.serialize().toByteArray();
    }

    @Override
    public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
//        System.out.println("fromBinaryJava");
        TraceSegment traceSegment = null;
        try {
            traceSegment = new TraceSegment(SegmentMessage.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return traceSegment;
    }
}
