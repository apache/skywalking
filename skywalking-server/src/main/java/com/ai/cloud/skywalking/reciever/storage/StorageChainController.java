package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;
import com.ai.cloud.skywalking.reciever.storage.chain.SaveToHBaseChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class StorageChainController {
    private static Logger logger = LogManager
            .getLogger(StorageChainController.class);

    private static List<IStorageChain> chainArray = new ArrayList<IStorageChain>();

    static {
        chainArray.add(new SaveToHBaseChain());
    }

    public static void doStorage(String buriedPointDatas) {
        String[] buriedPointData = buriedPointDatas.split(";");
        if (buriedPointData == null || buriedPointData.length == 0) {
            return;
        }
        for (String buriedPoint : buriedPointData) {
            try {
                if (buriedPoint == null || buriedPoint.trim().length() == 0) {
                    continue;
                }
                BuriedPointEntry entry = BuriedPointEntry.convert(buriedPoint);
                while(true) {
                    try {
                        Chain chain = new Chain(chainArray);
                        chain.doChain(entry, buriedPoint);
                        break;
                    } catch (Throwable e) {
                        Thread.sleep(Config.StorageChain.RETRY_STORAGE_WAIT_TIME);
                    }
                }
            } catch (Throwable e) {
                logger.error("ready to save buriedPoint error, choose to ignore. data="
                        + buriedPoint, e);
            }
        }
    }
}
