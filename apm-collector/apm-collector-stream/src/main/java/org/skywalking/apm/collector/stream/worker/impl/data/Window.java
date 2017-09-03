package org.skywalking.apm.collector.stream.worker.impl.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pengys5
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
