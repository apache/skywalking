package com.a.eye.skywalking.collector.worker.storage;

import java.util.HashMap;

/**
 * @author pengys5
 */
public abstract class Window<T extends Data> {

    private Pointer current;

    private WindowData<T> windowDataA;
    private WindowData<T> windowDataB;

    public Window() {
        windowDataA = new WindowData(new HashMap<>());
        windowDataB = new WindowData(new HashMap<>());
        current = Pointer.A;
    }

    public void switchPointer() {
        if (current.equals(Pointer.A)) {
            current = Pointer.B;
        } else {
            current = Pointer.A;
        }
    }

    protected WindowData<T> getCurrentAndHold() {
        if (Pointer.A.equals(current)) {
            windowDataA.hold();
            return windowDataA;
        } else {
            windowDataB.hold();
            return windowDataB;
        }
    }

    public WindowData<T> getLast() {
        if (Pointer.A.equals(current)) {
            return windowDataB;
        } else {
            return windowDataA;
        }
    }

    enum Pointer {
        A, B
    }
}
