package test.ai.cloud.serialize;

import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.protocol.AckSpan;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.common.CallType;
import com.ai.cloud.skywalking.protocol.common.SpanType;

public class SerializeTest {
    public static void main(String[] args) throws InterruptedException {
        Span spandata = new Span("1.0b.1461060884539.7d6d06e.22489.1271.103", "", 0);
        spandata.setSpanType(SpanType.LOCAL);
        spandata.setStartDate(System.currentTimeMillis() - 1000 * 60);
        AckSpan requestSpan = new AckSpan(spandata);
        ContextBuffer.save(requestSpan);

        Thread.sleep(60 * 1000 * 10);
    }
}
