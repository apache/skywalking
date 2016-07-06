package com.ai.cloud.skywalking.reciever.buffer;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.processor.AbstractSpanProcessor;
import com.ai.cloud.skywalking.reciever.processor.ProcessorFactory;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import com.ai.cloud.skywalking.serialize.SerializedFactory;
import com.ai.cloud.skywalking.util.AtomicRangeInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.DATA_CONFLICT_WAIT_TIME;
import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.PER_THREAD_MAX_BUFFER_NUMBER;

public class DataBufferThread extends Thread {

    private Logger             logger = LogManager.getLogger(DataBufferThread.class);
    private byte[][]           data   = new byte[PER_THREAD_MAX_BUFFER_NUMBER][];
    private AtomicRangeInteger index  = new AtomicRangeInteger(0, PER_THREAD_MAX_BUFFER_NUMBER);

    public DataBufferThread(int threadIdx) {
        super("DataBufferThread_" + threadIdx);
    }

    @Override
    public void run() {
        Map<Integer, List<AbstractDataSerializable>> serializeObjects;
        while (true) {
            serializeObjects = new HashMap<Integer, List<AbstractDataSerializable>>();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    continue;
                }

                AbstractDataSerializable serializeData = SerializedFactory.unSerialize(data[i]);
                List<AbstractDataSerializable> hasBeenSerializedObjects = serializeObjects.get(serializeData.getDataType());
                if (hasBeenSerializedObjects == null) {
                    serializeObjects.put(serializeData.getDataType(), new ArrayList<AbstractDataSerializable>());
                }
                serializeObjects.get(serializeData.getDataType()).add(serializeData);

                data[i] = null;
            }

            for (Map.Entry<Integer, List<AbstractDataSerializable>> entry : serializeObjects.entrySet()) {
                AbstractSpanProcessor processor = ProcessorFactory.chooseProcessor(entry.getKey());
                if (processor != null) {
                    processor.process(entry.getValue());
                }
            }

            try {
                Thread.sleep(Config.Buffer.MAX_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep.", e);
            }

        }
    }



    public void saveTemporarily(byte[] s) {
        int i = index.getAndIncrement();
        while (data[i] != null) {
            try {
                ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.WARNING, "DataBuffer index[" + i + "] data collision, service pausing. ");
                Thread.sleep(DATA_CONFLICT_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failure sleep.", e);
            }
        }
        ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer reveiving data.");

        data[i] = s;
    }
}
