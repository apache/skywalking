package com.ai.cloud.skywalking.reciever.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;
import com.ai.cloud.skywalking.reciever.storage.chain.SaveToHBaseChain;

public class StorageChainController {
	private static Logger logger = LogManager
			.getLogger(StorageChainController.class);
	
	private static Chain globalChain = new Chain();
	
	static{
		globalChain.addChain(new SaveToHBaseChain());
	}

	public static void doStorage(String buriedPointDatas) {
		String[] buriedPointData = buriedPointDatas.split(";");
		if (buriedPointData == null || buriedPointData.length == 0) {
			return;
		}
		for (String buriedPoint : buriedPointData) {
			try {
				if(buriedPoint == null || buriedPoint.trim().length() == 0){
					continue;
				}
				BuriedPointEntry entry = BuriedPointEntry.convert(buriedPoint);
				globalChain.doChain(entry, buriedPoint);
			} catch (Throwable e) {
				logger.error("ready to save buriedPoint error, choose to ignore. data="
						+ buriedPoint, e);
			}
		}
	}
}
