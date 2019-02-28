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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peng-yongsheng
 */
public abstract class Window<DATA> {

    private AtomicInteger windowSwitch = new AtomicInteger(0);

    private SWCollection<DATA> pointer;

    private SWCollection<DATA> windowDataA;
    private SWCollection<DATA> windowDataB;

    Window() {
        this(true);
    }

    Window(boolean autoInit) {
        if (autoInit) {
            init();
        }
    }

    protected void init() {
        this.windowDataA = collectionInstance();
        this.windowDataB = collectionInstance();
        this.pointer = windowDataA;
    }

    public abstract SWCollection<DATA> collectionInstance();

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

    SWCollection<DATA> getCurrentAndWriting() {
        if (pointer == windowDataA) {
            windowDataA.writing();
            return windowDataA;
        } else {
            windowDataB.writing();
            return windowDataB;
        }
    }

    private SWCollection<DATA> getCurrent() {
        return pointer;
    }

    public int currentCollectionSize() {
        return getCurrent().size();
    }

    public SWCollection<DATA> getLast() {
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
