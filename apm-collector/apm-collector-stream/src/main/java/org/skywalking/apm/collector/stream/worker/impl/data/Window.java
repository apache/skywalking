package org.skywalking.apm.collector.stream.worker.impl.data;

/**
 * @author pengys5
 */
public abstract class Window {

    private DataCollection pointer;

    private DataCollection windowDataA;
    private DataCollection windowDataB;

    public Window() {
        windowDataA = new DataCollection();
        windowDataB = new DataCollection();
        pointer = windowDataA;
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
}
