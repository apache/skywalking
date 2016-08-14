package test.a.eye.cloud.serialize;

import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.Span;
import com.a.eye.skywalking.protocol.common.SpanType;

public class SerializeTest {
    public static void main(String[] args) throws InterruptedException {
        while (true) {
            Span spandata = new Span("1.0b.1461060884539.7d6d06e.22489.1271.103", "", 0, "test-application", "test");
            spandata.setSpanType(SpanType.LOCAL);
            spandata.setStartDate(System.currentTimeMillis() - 1000 * 60);
            AckSpan requestSpan = new AckSpan(spandata);
            ContextBuffer.save(requestSpan);
            Thread.sleep(500);
        }

    }
}
