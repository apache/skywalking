package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

public interface RegistryApplication {
    String applicationCode();

    String expressValue();

    class Impl implements RegistryApplication {
        private String applicationCode;
        private String express;

        Impl(String code, String express) {
            this.applicationCode = code;
            this.express = express;
        }

        @Override
        public String applicationCode() {
            return applicationCode;
        }

        @Override public String expressValue() {
            return express;
        }
    }
}
