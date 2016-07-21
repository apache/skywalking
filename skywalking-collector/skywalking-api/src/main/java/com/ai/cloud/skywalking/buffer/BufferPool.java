package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.protocol.common.ISerializable;

import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.conf.Config.Buffer.POOL_SIZE;

/**
 * Created by wusheng on 16/7/21.
 */
class BufferPool {
    // 注意： 这个变量名如果改变需要改变test-api工程中的Config变量
    private static BufferGroup[] bufferGroups = new BufferGroup[POOL_SIZE];
    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            bufferGroups[i] = new BufferGroup("buffer_group-" + i);
        }
    }

    public void save(ISerializable data) {
        bufferGroups[ThreadLocalRandom.current().nextInt(0, POOL_SIZE)].save(data);
    }


}
