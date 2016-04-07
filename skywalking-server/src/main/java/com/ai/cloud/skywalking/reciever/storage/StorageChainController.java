package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Constants;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import com.ai.cloud.skywalking.reciever.storage.chain.AlarmChain;
import com.ai.cloud.skywalking.reciever.storage.chain.SaveToHBaseChain;
import com.ai.cloud.skywalking.reciever.storage.chain.SaveToMySQLChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.ai.cloud.skywalking.reciever.conf.Config.StorageChain.STORAGE_TYPE;

public class StorageChainController {
    private static Logger logger = LogManager
            .getLogger(StorageChainController.class);

    private static List<IStorageChain> chainArray = new ArrayList<IStorageChain>();

    static {
        if (STORAGE_TYPE.equalsIgnoreCase("hbase")) {
            chainArray.add(new AlarmChain());
            chainArray.add(new SaveToHBaseChain());
        } else if (STORAGE_TYPE.equalsIgnoreCase("mysql")) {
            chainArray.add(new SaveToMySQLChain());
        } else {
            throw new RuntimeException("illegal storage type.");
        }
    }

    public static void doStorage(String buriedPointDatas) {
        String[] buriedPointData = buriedPointDatas.split(Constants.DATA_SPILT);
        if (buriedPointData == null || buriedPointData.length == 0) {
            return;
        }
        List<Span> spans = new ArrayList<Span>();
        for (String buriedPoint : buriedPointData) {
            try {
                if (buriedPoint == null || buriedPoint.trim().length() == 0) {
                    continue;
                }
                spans.add(new Span(buriedPoint));
            } catch (Throwable e) {
                logger.error("ready to save buriedPoint error, choose to ignore. data="
                        + buriedPoint, e);
                ServerHealthCollector.getCurrentHeathReading("StorageChainController").updateData(ServerHeathReading.ERROR,
                        "ready to save buriedPoint error, choose to ignore. data=" + buriedPoint);
            }
        }

        try {
            Chain chain = new Chain(chainArray);
            chain.doChain(spans);
        } catch (Throwable e) {
            logger.error("Failed to storage chain.", e);
            ServerHealthCollector.getCurrentHeathReading("StorageChainController").updateData(ServerHeathReading.ERROR,
                    "Failed to storage chain.Cause:" + e.getMessage());
        }
    }
}
