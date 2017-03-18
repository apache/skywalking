package com.a.eye.skywalking.collector.worker.web.controller.tracedag;

import com.a.eye.skywalking.collector.worker.httpserver.Controller;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

/**
 * @author pengys5
 */
public class TraceDagLoadController extends Controller {

    @Override
    protected NanoHTTPD.Method httpMethod() {
        return NanoHTTPD.Method.GET;
    }

    @Override
    protected String path() {
        return "/traceDagLoad";
    }

    @Override
    public JsonElement execute(Map<String, String> parms) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("test", "aaaa");
        return jsonObject;
    }
}
