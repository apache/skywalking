/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker.impl.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peng-yongsheng
 */
public abstract class Window {

    private AtomicInteger windowSwitch = new AtomicInteger(0);

    private DataCollection pointer;

    private DataCollection windowDataA;
    private DataCollection windowDataB;

    public Window() {
        windowDataA = new DataCollection();
        windowDataB = new DataCollection();
        pointer = windowDataA;
    }

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

    protected DataCollection getCurrentAndWriting() {
        if (pointer == windowDataA) {
            windowDataA.writing();
            return windowDataA;
        } else {
            windowDataB.writing();
            return windowDataB;
        }
    }

    protected DataCollection getCurrent() {
        return pointer;
    }

    public DataCollection getLast() {
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
