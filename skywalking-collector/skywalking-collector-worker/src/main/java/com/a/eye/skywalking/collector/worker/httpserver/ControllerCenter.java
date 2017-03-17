package com.a.eye.skywalking.collector.worker.httpserver;

import fi.iki.elonen.NanoHTTPD;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public enum ControllerCenter {
    INSTANCE;

    private Map<String, Controller> getControllers = new ConcurrentHashMap();

    private Map<String, Controller> postControllers = new ConcurrentHashMap();

    protected void register(NanoHTTPD.Method method, String path, Controller controller) throws DuplicateControllerException {
        if (NanoHTTPD.Method.GET.equals(method)) {
            if (getControllers.containsKey(path)) {
                throw new DuplicateControllerException("method: " + method + "with path: " + path + " duplicate each other");
            } else {
                getControllers.put(path, controller);
            }
        } else if (NanoHTTPD.Method.POST.equals(method)) {
            if (postControllers.containsKey(path)) {
                throw new DuplicateControllerException("method: " + method + "with path: " + path + " duplicate each other");
            } else {
                postControllers.put(path, controller);
            }
        }
    }

    protected Controller find(NanoHTTPD.Method method, String path) {
        if (NanoHTTPD.Method.GET.equals(method)) {
            return getControllers.get(path);
        } else if (NanoHTTPD.Method.POST.equals(method)) {
            return postControllers.get(path);
        } else {
            return null;
        }
    }
}
