package org.skywalking.apm.agent.core.datacarrier.performance.comparetest.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class TestDisruptor {
    public static int totalSize = 100000000;
    public static long startTime;
    public static volatile boolean isEnd = false;

    public static void main(String[] args) throws InterruptedException {
        // The factory for the event
        DataEventFactory factory = new DataEventFactory();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;

        // Construct the Disruptor
        Disruptor<Data> disruptor = new Disruptor<Data>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWithWorkerPool(new WorkHandler<Data>(){
            @Override
            public void onEvent(Data event) throws Exception {
                System.out.println("work1:" + event.getValue1());
            }
        }, new WorkHandler<Data>(){

            @Override
            public void onEvent(Data event) throws Exception {
                System.out.println("work2:" + event.getValue1());
            }
        });
        // Connect the handler
        disruptor.handleEventsWith(new DataEventHandler());

        // Start the Disruptor, starts all threads running
        disruptor.start();

        RingBuffer<Data> ringBuffer = disruptor.getRingBuffer();
        DataProducer producer = new DataProducer(ringBuffer);

        startTime = System.currentTimeMillis();
        for (int i = 0; i < totalSize; i++) {
            Data data = new Data();
            data.setValue1(i);
            producer.onData(data);

            Thread.sleep(1000L);
        }

        disruptor.shutdown();

        while(!TestDisruptor.isEnd){
            Thread.sleep(100L);
        }
    }
}
