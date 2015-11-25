package com.ai.cloud.skywalking.reciever.storage;


import com.ai.cloud.skywalking.protocol.Span;

import java.util.List;

public interface IStorageChain {
    void doChain(List<Span> spans, Chain chain);
}
