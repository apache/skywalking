package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.List;

public interface RegistryOperationName {
    String applicationCode();

    List<String> operationName();

    class Impl implements RegistryOperationName {
        private final String code;
        private final List<String> express;

        Impl(String code, List<String> express) {
            this.code = code;
            this.express = express;
        }

        @Override public String applicationCode() {
            return code;
        }

        @Override public List<String> operationName() {
            return express;
        }
    }
}
