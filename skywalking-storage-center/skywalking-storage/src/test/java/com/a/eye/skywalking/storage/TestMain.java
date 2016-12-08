package com.a.eye.skywalking.storage;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by xin on 2016/12/7.
 */
public class TestMain {
    public static void main(String[] args) throws InterruptedException {
        Disruptor<StringBuilder> requestSpanDataDisruptor = null;
        requestSpanDataDisruptor = new Disruptor<StringBuilder>(new StringBuilderFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        requestSpanDataDisruptor.handleEventsWith(new EventHandler<StringBuilder>() {
            @Override
            public void onEvent(StringBuilder event, long sequence, boolean endOfBatch) throws Exception {
                System.out.println("AA: " + event);
            }
        }, new EventHandler<StringBuilder>() {
            @Override
            public void onEvent(StringBuilder event, long sequence, boolean endOfBatch) throws Exception {
                System.out.println("BB: " + event);
            }
        });
        requestSpanDataDisruptor.start();
        RingBuffer<StringBuilder> stringBuilderRingBuffer = requestSpanDataDisruptor.getRingBuffer();

        long sequence = stringBuilderRingBuffer.next();  // Grab the next sequence
        try {
            StringBuilder data = stringBuilderRingBuffer.get(sequence);
            data.append("A");
        } catch (Exception e) {
        } finally {
            stringBuilderRingBuffer.publish(sequence);
        }

        Thread.sleep(1000);
    }
}
