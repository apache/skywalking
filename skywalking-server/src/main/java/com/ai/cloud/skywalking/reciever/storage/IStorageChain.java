package com.ai.cloud.skywalking.reciever.storage;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;

import java.util.List;

public interface IStorageChain {
    void doChain(List<BuriedPointEntry> entry, Chain chain);
}
