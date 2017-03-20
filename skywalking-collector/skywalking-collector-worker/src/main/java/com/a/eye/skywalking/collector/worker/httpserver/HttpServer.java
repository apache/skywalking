package com.a.eye.skywalking.collector.worker.httpserver;

import com.google.gson.JsonElement;
import fi.iki.elonen.NanoHTTPD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

/**
 * @author pengys5
 */
public enum HttpServer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(HttpServer.class);

    public void boot() throws Exception {
        NanoHttpServer server = new NanoHttpServer(7001);
        ControllerCreator.INSTANCE.boot();
    }

    public class NanoHttpServer extends NanoHTTPD {

        public NanoHttpServer(int port) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            logger.info("Running! Point your browsers to http://localhost:%d/", port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            String uri = session.getUri();
            Map<String, String> parms = session.getParms();
            logger.debug("request method: %s, uri: %s, parms: %s", method.toString(), uri, parms);

            try {
                JsonElement response = RequestDispatcher.INSTANCE.dispatch(method, uri, parms);
                return newFixedLengthResponse(Response.Status.OK, "text/json", response.toString());
            } catch (ControllerNotFoundException e) {
                String errorMessage = e.getMessage();
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", errorMessage);
            }
        }
    }
}