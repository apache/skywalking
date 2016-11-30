package com.a.eye.skywalking.routing.disruptor.ack;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.routing.router.RoutingService;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.disruptor.AbstractSpanEventHandler;

import java.util.ArrayList;
import java.util.List;

public class AckSpanBufferEventHandler extends AbstractSpanEventHandler<AckSpanHolder> {
    private List<AckSpan> buffer = new ArrayList<>(bufferSize);

    public AckSpanBufferEventHandler(String connectionURl) {
        super(connectionURl);
    }

    @Override
    public String getExtraId() {
        return "AckSpanEventHandler";
    }

    @Override
    public void onEvent(AckSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        buffer.add(event.getAckSpan());

        if (stop){
            try {
                for (AckSpan ackSpan : buffer) {
                    SpanDisruptor spanDisruptor = RoutingService.getRouter().lookup(ackSpan);
                    spanDisruptor.saveSpan(ackSpan);
                }
            }finally {
                buffer.clear();
            }

            return ;
        }

        while (!previousSendResult){
            try {
                Thread.sleep(10L);
            }catch (InterruptedException e){
            }
        }

        if (endOfBatch || buffer.size() == bufferSize) {
            SpanStorageClient spanStorageClient = getStorageClient();
            spanStorageClient.sendACKSpan(buffer);
            buffer.clear();
        }
    }
}
