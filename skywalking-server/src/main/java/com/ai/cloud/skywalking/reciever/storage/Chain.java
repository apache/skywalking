package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

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
                    chains.get(index++).doChain(spans, this);
                    break;
                } catch (Throwable e) {
                    logger.error("do chain at index[" + (index - 1) + "] failure.", e);
                    ServerHealthCollector.getCurrentHeathReading("storage-chain").updateData(ServerHeathReading.ERROR,
                    		"do chain at index[" + (index - 1) + "] failure. spans list hash code:" + spans.hashCode() + ",Cause:" + e.getMessage());
                    // 如果Chain出现任何异常，将重做Chain,保证数据不丢失
                    index--;
                }
            }
        }

    }

    void addChain(IStorageChain chain) {
        chains.add(chain);
    }
}
