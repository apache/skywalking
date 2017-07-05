package org.skywalking.apm.agent.core.datacarrier.performance.comparetest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.Test;
import org.skywalking.apm.agent.core.datacarrier.DataCarrier;
import org.skywalking.apm.agent.core.datacarrier.consumer.IConsumer;

/**
 * Created by wusheng on 2016/11/24.
 */
public class VsABQ {
    private static int totalSize = 100000000;

    /**
     * 39469
     *
     * @throws InterruptedException
     */
    @Test
    public void testABQ() throws InterruptedException {
        final ArrayBlockingQueue queue = new ArrayBlockingQueue(5000);

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = -1;
                int dataCounter = 0;

                while (true) {
                    ArrayList data = new ArrayList();
                    queue.drainTo(data);
                    if (startTime == -1 && data.size() > 0) {
                        startTime = System.currentTimeMillis();
                    }

                    dataCounter += data.size();
                    if (dataCounter == totalSize) {
                        break;
                    }
                }

                System.out.println("time cost:" + (System.currentTimeMillis() - startTime));
            }
        });
        consumer.start();

        for (int i = 0; i < totalSize; i++) {
            boolean status = false;
            while (!status) {
                try {
                    queue.add(i);
                    status = true;
                } catch (Exception e) {
                }
            }
        }

        consumer.join();
    }

    public static void main(String[] args) throws InterruptedException {
        final DataCarrier<Integer> dataCarrier = new DataCarrier<Integer>(5, 1000);

        dataCarrier.consume(new IConsumer<Integer>() {
            long startTime = -1;
            int dataCounter = 0;

            @Override
            public void init() {

            }

            @Override
            public void consume(List<Integer> data) {
                if (startTime == -1 && data.size() > 0) {
                    startTime = System.currentTimeMillis();
                }
                dataCounter += data.size();

                if (dataCounter == totalSize) {
                    System.out.println("cost:" + (System.currentTimeMillis() - startTime));
                }
            }

            @Override
            public void onError(List<Integer> data, Throwable t) {

            }

            @Override
            public void onExit() {

            }
        }, 1);

        for (int i = 0; i < totalSize; i++) {
            dataCarrier.produce(i);
        }

        Thread.sleep(10 * 1000L);
    }
}
