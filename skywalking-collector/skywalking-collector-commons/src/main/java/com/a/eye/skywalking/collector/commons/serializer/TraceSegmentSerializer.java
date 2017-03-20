package com.a.eye.skywalking.collector.commons.serializer;

import akka.serialization.JSerializer;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.proto.SegmentMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class TraceSegmentSerializer extends JSerializer {
    private Logger logger = LogManager.getFormatterLogger(TraceSegmentSerializer.class);

    @Override
    public boolean includeManifest() {
        return false;
    }

    @Override
    public int identifier() {
        return 30;
    }

    @Override
    public byte[] toBinary(Object o) {
        TraceSegment traceSegment = (TraceSegment) o;
        return traceSegment.serialize().toByteArray();
    }

    @Override
    public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
        TraceSegment traceSegment = null;
        try {
            traceSegment = new TraceSegment(SegmentMessage.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            logger.warn("Can't covert message from byte[] to SegmentMessage");
        }
        return traceSegment;
    }
}
