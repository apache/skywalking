package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.context.Span;

import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.buffer.config.BufferConfig.POOL_MAX_SIZE;

class BufferPool {
    private static BufferGroup[] bufferGroups = new BufferGroup[POOL_MAX_SIZE];

    static {
        for (int i = 0; i < POOL_MAX_SIZE; i++) {
            bufferGroups[i] = new BufferGroup("BufferLine-" + i);
        }
    }

    public void save(Span span) {
        bufferGroups[ThreadLocalRandom.current().nextInt(0, POOL_MAX_SIZE)].save(span);
    }

}
