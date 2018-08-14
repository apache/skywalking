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

package org.apache.skywalking.apm.collector.core.cache;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peng-yongsheng
 */
public abstract class Window<WINDOW_COLLECTION extends Collection> {

    private AtomicInteger windowSwitch = new AtomicInteger(0);

    private WINDOW_COLLECTION pointer;

    private WINDOW_COLLECTION windowDataA;
    private WINDOW_COLLECTION windowDataB;

    protected Window() {
        this.windowDataA = collectionInstance();
        this.windowDataB = collectionInstance();
        this.pointer = windowDataA;
    }

    public abstract WINDOW_COLLECTION collectionInstance();

    public boolean trySwitchPointer() {
        return windowSwitch.incrementAndGet() == 1 && !getLast().isReading();
    }

    public void trySwitchPointerFinally() {
        windowSwitch.addAndGet(-1);
    }

    public void switchPointer() {
        if (pointer == windowDataA) {
            pointer = windowDataB;
        } else {
            pointer = windowDataA;
        }
        getLast().reading();
    }

    protected WINDOW_COLLECTION getCurrentAndWriting() {
        if (pointer == windowDataA) {
            windowDataA.writing();
            return windowDataA;
        } else {
            windowDataB.writing();
            return windowDataB;
        }
    }

    private WINDOW_COLLECTION getCurrent() {
        return pointer;
    }

    public int currentCollectionSize() {
        return getCurrent().size();
    }

    public WINDOW_COLLECTION getLast() {
        if (pointer == windowDataA) {
            return windowDataB;
        } else {
            return windowDataA;
        }
    }

    public void finishReadingLast() {
        getLast().clear();
        getLast().finishReading();
    }
}
