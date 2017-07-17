package org.skywalking.apm.commons.datacarrier.buffer;

import java.util.LinkedList;
import org.skywalking.apm.commons.datacarrier.common.AtomicRangeInteger;

/**
 * Created by wusheng on 2016/10/25.
 */
public class Buffer<T> {
    private final Object[] buffer;
    private BufferStrategy strategy;
    private AtomicRangeInteger index;

    Buffer(int bufferSize, BufferStrategy strategy) {
        buffer = new Object[bufferSize];
        this.strategy = strategy;
        index = new AtomicRangeInteger(0, bufferSize);
    }

    void setStrategy(BufferStrategy strategy) {
        this.strategy = strategy;
    }

    boolean save(T data) {
        int i = index.getAndIncrement();
        if (buffer[i] != null) {
            switch (strategy) {
                case BLOCKING:
                    while (buffer[i] != null) {
                        try {
                            Thread.sleep(1L);
                        } catch (InterruptedException e) {
                        }
                    }
                    break;
                case IF_POSSIBLE:
                    return false;
                case OVERRIDE:
                default:
            }
        }
        buffer[i] = data;
        return true;
    }

    public int getBufferSize() {
        return buffer.length;
    }

    public LinkedList<T> obtain(int start, int end) {
        LinkedList<T> result = new LinkedList<T>();
        for (int i = start; i < end; i++) {
            if (buffer[i] != null) {
                result.add((T)buffer[i]);
                buffer[i] = null;
            }
        }
        return result;
    }

}
