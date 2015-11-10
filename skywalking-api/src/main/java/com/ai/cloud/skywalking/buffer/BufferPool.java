package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.context.Span;

import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.conf.Config.Buffer.POOL_MAX_LENGTH;

class BufferPool {
    private static BufferGroup[] bufferGroups = new BufferGroup[POOL_MAX_LENGTH];

    static {
        for (int i = 0; i < POOL_MAX_LENGTH; i++) {
            bufferGroups[i] = new BufferGroup("BufferLine-" + i);
        }
    }

    public void save(Span span) {
        bufferGroups[ThreadLocalRandom.current().nextInt(0, POOL_MAX_LENGTH)].save(span);
    }

}
