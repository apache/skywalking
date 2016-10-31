package test.a.eye.cloud.assertspandata;

import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;

/**
 * Created by xin on 16-6-6.
 */
public class SDKGeneratedDataTest {

    @Test
    public void traceTreeAssertTest() {
        Config.Consumer.MAX_CONSUMER = 0;
        Span testSpan = new Span("1.0b.1465224457414.7e57f54.22905.61.2691", "", 0, "test-application", "5");
        RequestSpan requestSpan =
                RequestSpan.RequestSpanBuilder.newBuilder(testSpan).viewPoint("http://hire.asiainfo.com/Aisse-Mobile-Web/aisseWorkPage/submitReimbursement").build();
        ContextBuffer.save(requestSpan);
        RequestSpanAssert.assertEquals(new String[][] {{"0", "http://hire.asiainfo.com/Aisse-Mobile-Web/aisseWorkPage/submitReimbursement", null}});

    }
}
