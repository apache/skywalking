package com.a.eye.skywalking.collector.worker.httpserver;

import com.google.gson.JsonElement;
import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

/**
 * @author pengys5
 */
public enum RequestDispatcher {
    INSTANCE;

    public JsonElement dispatch(NanoHTTPD.Method method, String uri, Map<String, String> parms) throws ControllerNotFoundException {
        Controller controller = ControllerCenter.INSTANCE.find(method, uri);
        if (controller != null) {
            return controller.execute(parms);
        } else {
            throw new ControllerNotFoundException("Could not found controller for [method: " + method.name() + ", uri: " + uri + "]");
        }
    }
}
