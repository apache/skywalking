package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.AbstractAsyncMember;
import com.a.eye.skywalking.collector.actor.AbstractSyncMember;
import com.google.gson.JsonElement;
import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

/**
 * @author pengys5
 */
public abstract class Controller {

    protected abstract NanoHTTPD.Method httpMethod();

    protected abstract String path();

    protected abstract JsonElement execute(Map<String, String> parms);

    protected void tell(AbstractAsyncMember targetMember, Object message) throws Exception {
        targetMember.beTold(message);
    }

    protected void tell(AbstractSyncMember targetMember, Object message) throws Exception {
        targetMember.beTold(message);
    }
}