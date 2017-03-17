package com.a.eye.skywalking.collector.worker.httpserver;

/**
 * @author pengys5
 */
public abstract class ControllerProvider {

    public abstract Class clazz();

    public Controller create() throws Exception {
        Controller controller = (Controller) clazz().newInstance();
        return controller;
    }
}
