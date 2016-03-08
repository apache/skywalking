package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Chain {
	private static Logger logger = LogManager
            .getLogger(Chain.class);
	
    private List<IStorageChain> chains;

    private int index = 0;

    public Chain(List<IStorageChain> chains) {
        this.chains = chains;
    }

    public void doChain(List<Span> spans) {
        if (index < chains.size()) {
            while (true) {
                try {
                    chains.get(index).doChain(spans, this);
                    index++;
                    break;
                } catch (Throwable e) {
                	logger.error("do chain at index[{}] failure.", index, e);
                    ServerHealthCollector.getCurrentHeathReading("storage-chain").updateData(ServerHeathReading.ERROR,
                            "Failed to do chain action. spans list hash code:" + spans.hashCode() + ",Cause:" + e.getMessage());
                }
            }
        }
    }

    void addChain(IStorageChain chain) {
        chains.add(chain);
    }
}
