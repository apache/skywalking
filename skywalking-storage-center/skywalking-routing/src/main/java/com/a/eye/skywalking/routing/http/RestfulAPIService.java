package com.a.eye.skywalking.routing.http;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.routing.http.module.ResponseMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.a.eye.skywalking.routing.http.module.ResponseMessage.GET_NOT_SUPPORT;
import static com.a.eye.skywalking.routing.http.module.ResponseMessage.SERVER_ERROR;

public class RestfulAPIService extends NanoHTTPD {
    public static final String JSON_MIME_TYPE = "application/json";

    private static ILog logger = LogManager.getLogger(RestfulAPIService.class);
    private static final SpanStorageController spanController = new SpanStorageController();

    public RestfulAPIService(String host, int port) {
        super(host, port);
    }

    public void doStart() throws IOException {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            logger.info("Restful api service is up.\n");
        } catch (IOException e) {
            logger.error("Failed to start service.", e);
            throw e;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() != Method.POST) {
            return newFixedLengthResponse(Response.Status.OK, JSON_MIME_TYPE,
                    String.valueOf(GET_NOT_SUPPORT));
        }

        ResponseMessage responseMessage = ResponseMessage.NOT_FOUND;
        try {
            String postData = getPostData(session);
            if (spanController.isAddAckSpanURI(session.getUri())) {
                responseMessage = spanController.addAckSpans(postData);
            }

            if (spanController.isAddRequestSpanURI(session.getUri())) {
                responseMessage = spanController.addRequestSpans(postData);
            }

        } catch (Throwable e) {
            logger.error("server error.", e);
            responseMessage = SERVER_ERROR;
        }

        return newFixedLengthResponse(Response.Status.OK, JSON_MIME_TYPE, String.valueOf(responseMessage));
    }

    /**
     * Get the post data from request
     */
    private String getPostData(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> parameters = new HashMap<String, String>();
        session.parseBody(parameters);
        return parameters.get("postData");
    }


    public void doStop() {
        stop();
    }
}
