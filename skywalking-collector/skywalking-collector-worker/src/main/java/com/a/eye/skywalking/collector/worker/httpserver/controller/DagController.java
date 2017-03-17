package com.a.eye.skywalking.collector.worker.httpserver.controller;

import com.a.eye.skywalking.collector.worker.httpserver.Controller;
import com.a.eye.skywalking.collector.worker.httpserver.ControllerProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

/**
 * @author pengys5
 */
public class DagController extends Controller {

    @Override
    public NanoHTTPD.Method httpMethod() {
        return NanoHTTPD.Method.GET;
    }

    @Override
    public String path() {
        return "/getNodes";
    }

    @Override
    public JsonElement execute(Map<String, String> parms) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("test", "aaaa");
        return jsonObject;
    }

    public static class Factory extends ControllerProvider {
        @Override
        public Class clazz() {
            return DagController.class;
        }
    }
}
