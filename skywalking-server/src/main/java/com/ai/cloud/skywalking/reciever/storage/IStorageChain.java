package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;

public interface IStorageChain {
	public void doChain(BuriedPointEntry entry, String entryOriginData, Chain chain);
}
