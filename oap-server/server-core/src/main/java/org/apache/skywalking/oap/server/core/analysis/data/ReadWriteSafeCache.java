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
     * The buffer1 is used for read or write.
     */
    private BufferedData<T> buffer1;
    /**
     * The buffer2 is used for read or write.
     */
    private BufferedData<T> buffer2;

    /**
     * Read pointer should be pointing to {@link #buffer1} or {@link #buffer2}, and always not NULL.
     */
    private volatile BufferedData<T> readBufferPointer;
    /**
     * Write pointer should be pointing to {@link #buffer1} or {@link #buffer2}, and always not NULL.
     */
    private volatile BufferedData<T> writeBufferPointer;

    private final ReentrantLock lock;

    public ReadWriteSafeCache(BufferedData<T> buffer1, BufferedData<T> buffer2) {
        this.buffer1 = buffer1;
        this.buffer2 = buffer2;
        readBufferPointer = this.buffer1;
        writeBufferPointer = this.buffer2;
        lock = new ReentrantLock();
    }

    public void write(T data) {
        lock.lock();
        try {
            writeBufferPointer.accept(data);
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

            return readBufferPointer.read();
        } finally {
            lock.unlock();
        }
    }
}
