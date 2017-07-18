package org.skywalking.apm.collector.core.module;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public abstract class ModuleRegistration {

    public abstract Value buildValue();

    public static class Value {
        private final String host;
        private final int port;
        private final JsonObject data;

        public Value(String host, int port, JsonObject data) {
            this.host = host;
            this.port = port;
            this.data = data;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getHostPort() {
            return host + ":" + port;
        }

        public JsonObject getData() {
            return data;
        }
    }
}