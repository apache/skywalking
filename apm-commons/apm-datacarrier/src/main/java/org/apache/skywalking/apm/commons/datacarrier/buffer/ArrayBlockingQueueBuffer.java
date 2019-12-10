/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.commons.datacarrier.buffer;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The buffer implementation based on JDK ArrayBlockingQueue.
 *
 * This implementation has better performance in server side. We are still trying to research whether this is suitable
 * for agent side, which is more sensitive about blocks.
 *
 * @author wusheng
 */
public class ArrayBlockingQueueBuffer<T> implements QueueBuffer<T> {
    private BufferStrategy strategy;
    private ArrayBlockingQueue<T> queue;
    private int bufferSize;

    ArrayBlockingQueueBuffer(int bufferSize, BufferStrategy strategy) {
        this.strategy = strategy;
        this.queue = new ArrayBlockingQueue<T>(bufferSize);
        this.bufferSize = bufferSize;
    }

    @Override public boolean save(T data) {
        switch (strategy) {
            case IF_POSSIBLE:
                return queue.offer(data);
            default:
                try {
                    queue.put(data);
                } catch (InterruptedException e) {
                    // Ignore the error
                    return false;
                }
        }
        return true;
    }

    @Override public void setStrategy(BufferStrategy strategy) {
        this.strategy = strategy;
    }

    @Override public void obtain(List<T> consumeList) {
        queue.drainTo(consumeList);
    }

    @Override public int getBufferSize() {
        return bufferSize;
    }
}
