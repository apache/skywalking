package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.List;

public class Chain {
    private List<IStorageChain> chains;

    private int index = 0;

    public Chain(List<IStorageChain> chains) {
        this.chains = chains;
    }

    public void doChain(List<Span> spans) {
        if (index < chains.size()) {
            chains.get(index++).doChain(spans, this);
        }
    }

    void addChain(IStorageChain chain) {
        chains.add(chain);
    }
}
