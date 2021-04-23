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

package org.apache.skywalking.oap.server.core.analysis.data;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReadWriteSafeCache provides a read/write isolated cache.
 */
public class ReadWriteSafeCache<T> {
    /**
     * Pointer of read buffer.
     */
    private volatile BufferedData<T> readBufferPointer;
    /**
     * Pointer of write buffer.
     */
    private volatile BufferedData<T> writeBufferPointer;
    /**
     * Read/Write lock.
     */
    private final ReentrantLock lock;

    /**
     * Build the Cache through two given buffer instances.
     *
     * @param buffer1 read/write switchable buffer
     * @param buffer2 read/write switchable buffer. It is the write buffer at the beginning.
     */
    public ReadWriteSafeCache(BufferedData<T> buffer1, BufferedData<T> buffer2) {
        readBufferPointer = buffer1;
        writeBufferPointer = buffer2;
        lock = new ReentrantLock();
    }

    /**
     * Write the into the {@link #writeBufferPointer} buffer.
     *
     * @param data to enqueue.
     */
    public void write(T data) {
        lock.lock();
        try {
            writeBufferPointer.accept(data);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write the collection of data into the {@link #writeBufferPointer} buffer.
     *
     * @param data to enqueue.
     */
    public void write(List<T> data) {
        lock.lock();
        try {
            data.forEach(writeBufferPointer::accept);
        } finally {
            lock.unlock();
        }
    }

    public List<T> read() {
        lock.lock();
        try {
            // Switch the read and write pointers, when there is no writing.
            BufferedData<T> tempPointer = writeBufferPointer;
            writeBufferPointer = readBufferPointer;
            readBufferPointer = tempPointer;
        } finally {
            lock.unlock();
        }
        // Call read method outside of write lock for concurrency read-write.
        return readBufferPointer.read();
    }
}
