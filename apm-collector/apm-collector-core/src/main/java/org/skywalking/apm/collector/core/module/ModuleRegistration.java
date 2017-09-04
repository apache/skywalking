package org.skywalking.apm.collector.core.module;

/**
 * @author pengys5
 */
public abstract class ModuleRegistration {

    public abstract Value buildValue();

    public static class Value {
        private final String host;
        private final int port;
        private final String contextPath;

        public Value(String host, int port, String contextPath) {
            this.host = host;
            this.port = port;
            this.contextPath = contextPath;
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

        public String getContextPath() {
            return contextPath;
        }
    }
}