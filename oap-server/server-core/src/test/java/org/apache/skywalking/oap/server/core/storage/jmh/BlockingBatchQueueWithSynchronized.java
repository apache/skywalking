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

package org.apache.skywalking.oap.server.core.storage.jmh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@RequiredArgsConstructor
public class BlockingBatchQueueWithSynchronized<E> implements BlockingBatchQueue<E> {

    @Getter
    private final int maxBatchSize;

    @Getter
    private boolean inAppendingMode = true;

    private final List<E> elementData = new ArrayList<>(50000);

    public void putMany(List<E> elements) {
        synchronized (elementData) {
            elementData.addAll(elements);
            if (elementData.size() >= maxBatchSize) {
                elementData.notifyAll();
            }
        }
    }

    public List<E> popMany() throws InterruptedException {
        synchronized (elementData) {
            while (this.elementData.size() < maxBatchSize && inAppendingMode) {
                elementData.wait(1000);
            }
            if (CollectionUtils.isEmpty(elementData)) {
                return Collections.EMPTY_LIST;
            }
            List<E> sublist = this.elementData.subList(
                0, Math.min(maxBatchSize, this.elementData.size()));
            List<E> partition = new ArrayList<>(sublist);
            sublist.clear();
            return partition;
        }
    }

    public void noFurtherAppending() {
        synchronized (elementData) {
            inAppendingMode = false;
            elementData.notifyAll();
        }
    }

    public void furtherAppending() {
        synchronized (elementData) {
            inAppendingMode = true;
            elementData.notifyAll();
        }
    }

    @Override
    public int size() {
        return elementData.size();
    }
}
