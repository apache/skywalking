package com.a.eye.skywalking.routing.router;

import com.a.eye.skywalking.routing.config.Config;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by xin on 2016/12/1.
 */
public class DisruptorTest {

    private static Disruptor<StringBuilder> requestSpanDisruptor;
    private static RingBuffer<StringBuilder> requestSpanRingBuffer;

    private static class XXThread extends Thread {
        private int count = 0;

        @Override
        public void run() {
            while (true) {
                long sequence = requestSpanRingBuffer.next();
                try {
                    StringBuilder data = requestSpanRingBuffer.get(sequence);
                    data.append(count++);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("a");
                    requestSpanRingBuffer.publish(sequence);
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        requestSpanDisruptor = new Disruptor<StringBuilder>(new StringFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        requestSpanDisruptor.handleEventsWith(new EventHandler<StringBuilder>() {
            @Override
            public void onEvent(StringBuilder event, long sequence, boolean endOfBatch) throws Exception {
                System.out.println(endOfBatch + " : " + event);
            }
        });
        requestSpanDisruptor.start();
        requestSpanRingBuffer = requestSpanDisruptor.getRingBuffer();


        XXThread a = new XXThread();
        a.setDaemon(true);
        a.start();

        Thread.sleep(5 * 1000);
        System.out.println("shuting down");
        requestSpanDisruptor.shutdown();

        System.out.println("aaa");

        Thread.sleep(10 * 1000);
    }
}
