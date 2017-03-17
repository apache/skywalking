package com.a.eye.skywalking.collector.worker.httpserver;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum ControllerCreator {
    INSTANCE;

    public void boot() throws Exception {
        ServiceLoader<ControllerProvider> controllerLoader = java.util.ServiceLoader.load(ControllerProvider.class);
        for (ControllerProvider provider : controllerLoader) {
            Controller controller = provider.create();
            ControllerCenter.INSTANCE.register(controller.httpMethod(), controller.path(), controller);
        }
    }
}
