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

package org.apache.skywalking.oap.server.core.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@RequiredArgsConstructor
public class BlockingBatchQueueWithReentrantLock<E> implements BlockingBatchQueue<E> {

    @Getter
    private final int maxBatchSize;

    @Getter
    private volatile boolean inAppendingMode = true;

    private final List<E> elementData = new ArrayList<>(50000);

    private ReentrantLock reentrantLock = new ReentrantLock();
    private Condition condition = this.reentrantLock.newCondition();

    public void offer(List<E> elements) {
        reentrantLock.lock();
        try {
            elementData.addAll(elements);
            if (elementData.size() >= maxBatchSize) {
                condition.signalAll();
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    public List<E> poll() throws InterruptedException {
        reentrantLock.lock();
        try {
            while (this.elementData.size() < maxBatchSize && inAppendingMode) {
                condition.await(1000, TimeUnit.MILLISECONDS);
            }
            if (CollectionUtils.isEmpty(elementData)) {
                return Collections.EMPTY_LIST;
            }
            List<E> sublist = this.elementData.subList(
                0, Math.min(maxBatchSize, this.elementData.size()));
            List<E> partition = new ArrayList<>(sublist);
            sublist.clear();
            return partition;
        } finally {
            reentrantLock.unlock();
        }
    }

    public void noFurtherAppending() {
        reentrantLock.lock();
        try {
            inAppendingMode = false;
            condition.signalAll();
        } finally {
            reentrantLock.unlock();
        }
    }

    public void furtherAppending() {
        reentrantLock.lock();
        try {
            inAppendingMode = true;
            condition.signalAll();
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public int size() {
        return elementData.size();
    }
}
