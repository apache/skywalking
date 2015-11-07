package com.ai.cloud.skywalking.buffer;


import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.sender.DataSenderFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.cloud.skywalking.buffer.config.BufferConfig.*;

public class BufferGroup {
    public static CountDownLatch count;
    private String groupName;
    private Span[] dataBuffer = new Span[GROUP_MAX_SIZE];
    AtomicInteger index = new AtomicInteger(0);


    public BufferGroup(String groupName) {
        this.groupName = groupName;
        int k = 0, l;
        for (int i = 0; i < MAX_WORKER; i++) {
            l = GROUP_MAX_SIZE / MAX_WORKER + k;
            if (l + GROUP_MAX_SIZE / MAX_WORKER > GROUP_MAX_SIZE)
                l = GROUP_MAX_SIZE;
            new ConsumerWorker(k, l).start();
            k = l;
        }
    }

    public void save(Span span) {
        int i = Math.abs(index.getAndIncrement() % GROUP_MAX_SIZE);
        if (dataBuffer[i] != null) {
            // TODO  需要上报
            System.out.println(span.getLevelId() + "在Group[" + groupName + "]的第" + i + "位冲突");
        }
        dataBuffer[i] = span;
    }

    class ConsumerWorker extends Thread {
        private int start = 0;
        private int end = GROUP_MAX_SIZE;
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
            while (true) {
                boolean bool = false;
                StringBuilder data = new StringBuilder();
                for (int i = start; i < end; i++) {
                    if (dataBuffer[i] == null) {
                        continue;
                    }
                    bool = true;
                    data.append(dataBuffer[i] + ";");
                    dataBuffer[i++] = null;
                    if (i == SEND_MAX_SIZE) {
                        // TODO 发送失败了怎么办？
                        DataSenderFactory.getSender().send(data.toString());
                        i = 0;
                        data = new StringBuilder();
                    }
                }

                if (!bool) {
                    try {
                        Thread.sleep(5L);
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
