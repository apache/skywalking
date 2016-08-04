package com.ai.cloud.skywalking.reciever.model;

import com.ai.cloud.skywalking.protocol.AckSpan;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.TransportPackager;
import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.common.ISerializable;
import com.ai.cloud.skywalking.protocol.common.SpanType;
import com.ai.cloud.skywalking.protocol.util.IntegerAssist;
import com.ai.cloud.skywalking.reciever.buffer.BufferDataAssist;
import com.ai.cloud.skywalking.reciever.peresistent.BufferFileReader;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ai.cloud.skywalking.protocol.util.ByteDataUtil.unpackCheckSum;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BufferDataAssistTest {
    private List<ISerializable> serializableList;

    @Test
    public void pack() throws Exception {
        byte[] byteData =
                BufferDataAssist.appendLengthAndSplit(unpackCheckSum(TransportPackager.pack(serializableList)));

        int length = IntegerAssist.bytesToInt(Arrays.copyOfRange(byteData, 0, 4), 0);
        byte[] serializableByteData = Arrays.copyOfRange(byteData, 4, length + 4);
        List<AbstractDataSerializable> serializables = BufferFileReader.deserializableObjects(serializableByteData);
        assertEquals(2, serializables.size());
        assertNotNull(serializables.get(0));

    }

    @Before
    public void initData() {
        serializableList = new ArrayList<ISerializable>();
        Span span = new Span("test-traceID", "test", "10");
        span.setStartDate(System.currentTimeMillis() - 1000 * 10);
        span.setViewPointId("test-viewpoint");
        span.setSpanType(SpanType.LOCAL);
        serializableList.add(new RequestSpan(span));
        serializableList.add(new AckSpan(span));
    }

}
