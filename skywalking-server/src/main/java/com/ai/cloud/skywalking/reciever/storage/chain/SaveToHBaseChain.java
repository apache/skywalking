package com.ai.cloud.skywalking.reciever.storage.chain;

import org.apache.commons.lang.StringUtils;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;

public class SaveToHBaseChain implements IStorageChain{

	@Override
	public void doChain(BuriedPointEntry entry, String entryOriginData, Chain chain) {
        if (StringUtils.isEmpty(entry.getParentLevel().trim())) {
            HBaseOperator.insert(entry.getTraceId(), String.valueOf(entry.getLevelId()), entryOriginData);
        } else {
            HBaseOperator.insert(entry.getTraceId(), entry.getParentLevel() + "." + entry.getLevelId(), entryOriginData);
        }
        
        
	}

}
