package com.a.eye.skywalking.routing.http;

import com.google.gson.reflect.TypeToken;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.dependencies.com.google.gson.Gson;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.listener.server.SpanStorageServerListener;
import com.a.eye.skywalking.routing.http.module.AckSpanModule;
import com.a.eye.skywalking.routing.http.module.RequestSpanModule;
import com.a.eye.skywalking.routing.http.module.ResponseMessage;
import com.a.eye.skywalking.routing.listener.SpanStorageListenerImpl;

import java.util.List;

import static com.a.eye.skywalking.routing.http.module.ResponseMessage.OK;

public class SpanStorageController {

    private static String ADD_REQUEST_SPAN_URI = "/spans/request";
    private static String ADD_ACK_SPANS_URI = "/spans/ack";

    private SpanStorageServerListener spanStorageServerListener = new SpanStorageListenerImpl();

    /**
     * add request spans
     *
     * @param jsonData the json data of span
     */
    public ResponseMessage addRequestSpans(String jsonData) {
        List<RequestSpanModule> requestSpanModules = new Gson().fromJson(jsonData,
                new TypeToken<List<RequestSpanModule>>() {
                }.getType());
        for (RequestSpanModule span : requestSpanModules) {
            RequestSpan requestSpan = span.convertToGRPCModule();
            if (requestSpan != null) {
                spanStorageServerListener.storage(requestSpan);
            }
        }
        return OK;
    }


    /**
     * add ack spans
     *
     * @param jsonData the json data of span
     */
    public ResponseMessage addAckSpans(String jsonData) {
        List<AckSpanModule> requestSpanModules = new Gson().fromJson(jsonData,
                new TypeToken<List<AckSpanModule>>() {
                }.getType());
        for (AckSpanModule span : requestSpanModules) {
            AckSpan ackSpan = span.convertToGRPCModule();
            if (ackSpan != null) {
                spanStorageServerListener.storage(ackSpan);
            }
        }

        return OK;
    }

    public boolean isAddRequestSpanURI(String uri){
        return ADD_REQUEST_SPAN_URI.equals(uri);
    }

    public boolean isAddAckSpanURI(String uri){
        return ADD_ACK_SPANS_URI.equals(uri);
    }

}
