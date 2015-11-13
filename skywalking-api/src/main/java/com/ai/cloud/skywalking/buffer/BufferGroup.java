package com.ai.cloud.skywalking.buffer;


import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.sender.DataSenderFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.cloud.skywalking.conf.Config.Buffer.BUFFER_MAX_SIZE;
import static com.ai.cloud.skywalking.conf.Config.Consumer.MAX_CONSUMER;
import static com.ai.cloud.skywalking.conf.Config.Consumer.MAX_WAIT_TIME;
import static com.ai.cloud.skywalking.conf.Config.Sender.MAX_BUFFER_DATA_SIZE;

public class BufferGroup {
    public static CountDownLatch count;
    private String groupName;
    private Span[] dataBuffer = new Span[BUFFER_MAX_SIZE];
    AtomicInteger index = new AtomicInteger(0);

    public BufferGroup(String groupName) {
        this.groupName = groupName;

        int step = (int) Math.ceil(BUFFER_MAX_SIZE * 1.0 / MAX_CONSUMER);
        int start = 0, end = 0;
        while (true) {
            if (end + step >= BUFFER_MAX_SIZE) {
                new ConsumerWorker(start, BUFFER_MAX_SIZE).start();
                break;
            }
            end += step;
            new ConsumerWorker(start, end).start();
            start = end;
        }
    }

    public void save(Span span) {
        int i = Math.abs(index.getAndIncrement() % BUFFER_MAX_SIZE);
        if (dataBuffer[i] != null) {
            // TODO  需要上报
            System.out.println(span.getLevelId() + "在Group[" + groupName + "]的第" + i + "位冲突");
        }
        dataBuffer[i] = span;
    }

    class ConsumerWorker extends Thread {
        private int start = 0;
        private int end = BUFFER_MAX_SIZE;
        private StringBuilder builder = new StringBuilder();

        private ConsumerWorker(int start, int end) {
            this.start = start;
            this.end = end;
        }

        ConsumerWorker() {
        }

        @Override
        public void run() {
            int index = 0;
            StringBuilder data = new StringBuilder();
            while (true) {
                boolean bool = false;
                for (int i = start; i < end; i++) {
                    if (dataBuffer[i] == null) {
                        continue;
                    }
                    bool = true;
                    data.append(dataBuffer[i]);
                    dataBuffer[i] = null;
                    if (index++ == MAX_BUFFER_DATA_SIZE || data.length() >= Config.Sender.MAX_SEND_LENGTH) {
                        while (!DataSenderFactory.getSender().send(data.toString())) {
                            try {
                                Thread.sleep(50L);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        index = 0;
                        data = new StringBuilder();
                    }
                }

                if (!bool) {
                    try {
                        Thread.sleep(MAX_WAIT_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public String getGroupName() {
        return groupName;
    }

}
