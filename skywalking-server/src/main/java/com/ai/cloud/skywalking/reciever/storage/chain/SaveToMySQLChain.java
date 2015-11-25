package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;

import java.util.List;

public class SaveToMySQLChain implements IStorageChain {

    @Override
    public void doChain(List<Span> entry, Chain chain) {
        // TODO Auto-generated method stub

    }

}
