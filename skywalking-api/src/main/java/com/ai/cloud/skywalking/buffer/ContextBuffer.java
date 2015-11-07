package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.context.Span;

public class ContextBuffer {
    static BufferPool pool = new BufferPool();

    public static void save(Span span) {
        pool.save(span);
    }
}
