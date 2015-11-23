package com.ai.cloud.skywalking.buffer;


import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.selfexamination.HealthCollector;
import com.ai.cloud.skywalking.selfexamination.HeathReading;
import com.ai.cloud.skywalking.sender.DataSenderFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.Buffer.BUFFER_MAX_SIZE;
import static com.ai.cloud.skywalking.conf.Config.Consumer.*;

public class BufferGroup {
    private static Logger logger = Logger.getLogger(BufferGroup.class.getName());
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
        	HealthCollector.getCurrentHeathReading(null).updateData(HeathReading.WARNING, span.getLevelId() + "在Group[" + groupName + "]的第" + i + "位冲突");
        }
        dataBuffer[i] = span;
    }

    class ConsumerWorker extends Thread {
        private int start = 0;
        private int end = BUFFER_MAX_SIZE;

        private ConsumerWorker(int start, int end) {
        	super("ConsumerWorker");
            this.start = start;
            this.end = end;
        }

        @Override
        public void run() {
            StringBuilder data = new StringBuilder();
            while (true) {
                boolean bool = false;
                for (int i = start; i < end; i++) {
                    if (dataBuffer[i] == null) {
                        continue;
                    }
                    bool = true;
                    if (data.length() + dataBuffer[i].toString().length() >= Config.Sender.MAX_SEND_LENGTH) {
                        while (!DataSenderFactory.getSender().send(data.toString())) {
                            try {
                                Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
                            } catch (InterruptedException e) {
                                logger.log(Level.ALL, "Sleep Failure");
                            }
                        }
                        HealthCollector.getCurrentHeathReading(null).updateData(HeathReading.INFO, "send buried-point data.");
                        data = new StringBuilder();
                    }

                    data.append(dataBuffer[i] + ";");
                    dataBuffer[i] = null;
                }

                if (data != null && data.length() > 0) {
                    while (!DataSenderFactory.getSender().send(data.toString())) {
                        try {
                            Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
                        } catch (InterruptedException e) {
                            logger.log(Level.ALL, "Sleep Failure");
                        }
                    }
                    data = new StringBuilder();
                }

                if (!bool) {
                    try {
                        Thread.sleep(MAX_WAIT_TIME);
                    } catch (InterruptedException e) {
                        logger.log(Level.ALL, "Sleep Failure");
                    }
                }
            }
        }
    }

    public String getGroupName() {
        return groupName;
    }

}
