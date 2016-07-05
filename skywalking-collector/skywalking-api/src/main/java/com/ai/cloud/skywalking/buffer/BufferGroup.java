package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.protocol.common.ISerializable;
import com.ai.cloud.skywalking.selfexamination.HeathReading;
import com.ai.cloud.skywalking.selfexamination.SDKHealthCollector;
import com.ai.cloud.skywalking.sender.DataSenderFactoryWithBalance;
import com.ai.cloud.skywalking.util.AtomicRangeInteger;

import java.util.ArrayList;
import java.util.List;

import static com.ai.cloud.skywalking.conf.Config.Buffer.BUFFER_MAX_SIZE;
import static com.ai.cloud.skywalking.conf.Config.Consumer.*;

public class BufferGroup {
    private static Logger logger = LogManager.getLogger(BufferGroup.class);
    private String groupName;
    //注意： 修改这个变量名，需要修改test-api工程的Config类中的SPAN_ARRAY_FIELD_NAME变量
    private ISerializable[] dataBuffer = new ISerializable[BUFFER_MAX_SIZE];
    AtomicRangeInteger index = new AtomicRangeInteger(0, BUFFER_MAX_SIZE);

    public BufferGroup(String groupName) {
        this.groupName = groupName;
        startConsumerWorker();
    }

    private void startConsumerWorker() {
        if (MAX_CONSUMER > 0) {
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
    }

    public void save(ISerializable data) {
        int i = index.getAndIncrement();
        if (dataBuffer[i] != null) {
            logger.warn(
                    "Group[{}] index[{}] data collision, discard old data.",
                    groupName, i);
            SDKHealthCollector.getCurrentHeathReading("BufferGroup").updateData(HeathReading.WARNING, "BufferGroup index[" + i + "] data collision, data been coverd.");
        }
        dataBuffer[i] = data;
        SDKHealthCollector.getCurrentHeathReading("BufferGroup").updateData(HeathReading.INFO, "save span");
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
            List<ISerializable> packageData = new ArrayList<ISerializable>();
            while (true) {
                boolean bool = false;
                try {
                    for (int i = start; i < end; i++) {
                        if (dataBuffer[i] == null) {
                            continue;
                        }
                        bool = true;
                        if (packageData.size() >= Config.Sender.MAX_SEND_DATA_SIZE) {
                            while (!DataSenderFactoryWithBalance.getSender()
                                    .send(packageData)) {
                                try {
                                    Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
                                } catch (InterruptedException e) {
                                    logger.error("Sleep Failure");
                                }
                            }
                            logger.debug("send buried-point data, size:{}", packageData.size());
                            packageData = new ArrayList<ISerializable>();
                        }

                        packageData.add(dataBuffer[i]);
                        dataBuffer[i] = null;
                    }

                    if (packageData != null && packageData.size() > 0) {
                        while (!DataSenderFactoryWithBalance.getSender().send(
                                packageData)) {
                            try {
                                Thread.sleep(CONSUMER_FAIL_RETRY_WAIT_INTERVAL);
                            } catch (InterruptedException e) {
                                logger.error("Sleep Failure");
                            }
                        }
                        packageData = new ArrayList<ISerializable>();
                    }
                } catch (Throwable e) {
                    logger.error("buffer group running failed", e);
                }

                if (!bool) {
                    try {
                        Thread.sleep(MAX_WAIT_TIME);
                    } catch (InterruptedException e) {
                        logger.error("Sleep Failure");
                    }
                }
            }
        }
    }

    public String getGroupName() {
        return groupName;
    }

}
