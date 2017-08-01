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
        if (windowSwitch.incrementAndGet() == 1) {
            return true;
        } else {
            windowSwitch.addAndGet(-1);
            return false;
        }
    }

    public void switchPointer() {
        if (pointer == windowDataA) {
            pointer = windowDataB;
        } else {
            pointer = windowDataA;
        }
    }

    protected DataCollection getCurrentAndHold() {
        if (pointer == windowDataA) {
            windowDataA.hold();
            return windowDataA;
        } else {
            windowDataB.hold();
            return windowDataB;
        }
    }

    public DataCollection getLast() {
        if (pointer == windowDataA) {
            return windowDataB;
        } else {
            return windowDataA;
        }
    }

    public void releaseLast() {
        getLast().clear();
        windowSwitch.addAndGet(-1);
    }
}
