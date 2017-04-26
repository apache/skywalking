package com.a.eye.skywalking.collector.worker.storage;

import java.util.HashMap;

/**
 * @author pengys5
 */
public abstract class Window<T extends Data> {

    private WindowData<T> pointer;

    private WindowData<T> windowDataA;
    private WindowData<T> windowDataB;

    public Window() {
        windowDataA = new WindowData(new HashMap<>());
        windowDataB = new WindowData(new HashMap<>());
        pointer = windowDataA;
    }

    public void switchPointer() {
        if (pointer == windowDataA) {
            pointer = windowDataB;
        } else {
            pointer = windowDataA;
        }
    }

    protected WindowData<T> getCurrentAndHold() {
        if (pointer == windowDataA) {
            windowDataA.hold();
            return windowDataA;
        } else {
            windowDataB.hold();
            return windowDataB;
        }
    }

    public WindowData<T> getLast() {
        if (pointer == windowDataA) {
            return windowDataB;
        } else {
            return windowDataA;
        }
    }
}
