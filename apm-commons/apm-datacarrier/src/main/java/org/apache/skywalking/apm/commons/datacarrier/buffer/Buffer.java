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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.callback.QueueBlockingCallback;
import org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeInteger;

/**
 * Created by wusheng on 2016/10/25.
 */
public class Buffer<T> {
    private final BufferItem[] buffer;
    private BufferStrategy strategy;
    private AtomicRangeInteger index;
    private List<QueueBlockingCallback<T>> callbacks;

    Buffer(int bufferSize, BufferStrategy strategy) {
        buffer = new BufferItem[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            buffer[i] = new BufferItem();
        }
        this.strategy = strategy;
        index = new AtomicRangeInteger(0, bufferSize);
        callbacks = new LinkedList<QueueBlockingCallback<T>>();
    }

    void setStrategy(BufferStrategy strategy) {
        this.strategy = strategy;
    }

    void addCallback(QueueBlockingCallback<T> callback) {
        callbacks.add(callback);
    }

    boolean save(T data) {
        int i = index.getAndIncrement();
        BufferItem bufferItem = buffer[i];
        if (bufferItem.hasData()) {
            switch (strategy) {
                case BLOCKING:
                    boolean isFirstTimeBlocking = true;
                    while (bufferItem.hasData()) {
                        if (isFirstTimeBlocking) {
                            isFirstTimeBlocking = false;
                            for (QueueBlockingCallback<T> callback : callbacks) {
                                callback.notify(data);
                            }
                        }
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
        bufferItem.setItem(data);
        return true;
    }

    public int getBufferSize() {
        return buffer.length;
    }

    public void obtain(List<T> consumeList) {
        this.obtain(consumeList, 0, buffer.length);
    }

    public void obtain(List<T> consumeList, int start, int end) {
        for (int i = start; i < end; i++) {
            Object dataItem = buffer[i].getItem();
            if (dataItem != null) {
                consumeList.add((T)dataItem);
                buffer[i].clear();
            }
        }
    }

}
