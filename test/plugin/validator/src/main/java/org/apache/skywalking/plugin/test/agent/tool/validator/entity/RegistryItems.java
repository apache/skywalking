package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.List;

public interface RegistryItems {
    List<RegistryApplication> applications();

    List<RegistryInstance> instances();

    List<RegistryOperationName> operationNames();
}
