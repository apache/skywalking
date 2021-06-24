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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlockingBatchQueueWithLinkedBlockingQueue<E> implements BlockingBatchQueue<E> {

    @Getter
    private final int maxBatchSize;

    @Getter
    private volatile boolean inAppendingMode = true;

    private final LinkedBlockingQueue<E> elementData = new LinkedBlockingQueue<>();

    public void offer(List<E> elements) {
        elementData.addAll(elements);
    }

    public List<E> poll() throws InterruptedException {
        List<E> result = new ArrayList<>();
        do {
            E take = elementData.poll(1000, TimeUnit.MILLISECONDS);
            if (take != null) {
                result.add(take);
            }
            if (result.size() >= maxBatchSize) {
                return result;
            }
            if (!inAppendingMode && this.elementData.isEmpty()) {
                return result;
            }
        }
        while (!this.elementData.isEmpty());
        return result;

    }

    public void noFurtherAppending() {
        inAppendingMode = false;
    }

    public void furtherAppending() {
        inAppendingMode = true;
    }

    @Override
    public int size() {
        return this.elementData.size();
    }
}
