package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;

import java.util.List;

public class Chain {
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
