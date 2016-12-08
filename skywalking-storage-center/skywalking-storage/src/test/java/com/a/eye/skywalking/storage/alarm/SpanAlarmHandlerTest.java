package com.a.eye.skywalking.storage.alarm;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.storage.alarm.sender.AlarmMessageSender;
import com.a.eye.skywalking.storage.alarm.sender.AlarmMessageSenderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AlarmMessageSenderFactory.class)
public class SpanAlarmHandlerTest {

    @Mock
    private AlarmMessageSender messageHandler;
    @InjectMocks
    private SpanAlarmHandler handler;
    private AckSpan normalAckSpan;
    private AckSpan costMuchSpan;
    private AckSpan costTooMuchSpan;
    private AckSpan exceptionSpan;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(AlarmMessageSenderFactory.class);
        when(AlarmMessageSenderFactory.getSender()).thenReturn(messageHandler);
        long startTime = System.currentTimeMillis();
        AckSpan.Builder builder = AckSpan.newBuilder().setApplicationCode("test").setUsername("test").setCost(20)
                .setStatusCode(0).setLevelId(0).setParentLevel("0.0").setTraceId(TraceId.newBuilder()
                        .addSegments(2016).addSegments(startTime).addSegments(2).addSegments(100).addSegments(30)
                        .addSegments(1).build());

        normalAckSpan = builder.build();
        costMuchSpan = builder.setCost(600).build();
        costTooMuchSpan = builder.setCost(4000).build();
        exceptionSpan = builder.setCost(20).setStatusCode(1).setExceptionStack("occur exception").build();
    }

    @Test
    public void testNormalSpan() throws Exception {
        handler.onEvent(normalAckSpan, 1, false);
        verify(messageHandler, never()).send(any(), anyString());
    }

    @Test
    public void testCostMuchSpan() throws Exception {
        handler.onEvent(costMuchSpan, 1, false);
        verify(messageHandler, times(1)).send(any(), anyString());
    }

    @Test
    public void testExceptionSpan() throws Exception {
        handler.onEvent(exceptionSpan, 1, false);
        verify(messageHandler, times(1)).send(any(), anyString());
    }

    @Test
    public void testCostTooMuchSpan() throws Exception {
        handler.onEvent(costTooMuchSpan,1, false);
        verify(messageHandler, times(1)).send(any(), anyString());
    }
}

