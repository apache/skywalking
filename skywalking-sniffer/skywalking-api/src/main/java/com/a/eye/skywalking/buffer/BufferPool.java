package com.a.eye.skywalking.buffer;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.protocol.common.ISerializable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by wusheng on 16/7/21.
 */
class BufferPool {
    // 注意： 这个变量名如果改变需要改变test-api工程中的Config变量
    private static BufferGroup[] bufferGroups = new BufferGroup[Config.Buffer.POOL_SIZE];
    static {
        for (int i = 0; i < Config.Buffer.POOL_SIZE; i++) {
            bufferGroups[i] = new BufferGroup("buffer_group-" + i);
        }
    }

    public void save(ISerializable data) {
        bufferGroups[ThreadLocalRandom.current().nextInt(0, Config.Buffer.POOL_SIZE)].save(data);
    }


}
