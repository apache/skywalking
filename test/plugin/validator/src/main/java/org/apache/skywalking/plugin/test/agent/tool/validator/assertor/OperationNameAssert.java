package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import java.util.List;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ActualRegistryOperationNameEmptyException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryOperationNameNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryOperationNamesOfApplicationNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.RegistryOperationName;

public class OperationNameAssert {
    public static void assertEquals(List<RegistryOperationName> expected, List<RegistryOperationName> actual) {
        if (expected == null) {
            return;
        }

        for (RegistryOperationName operationName : expected) {
            RegistryOperationName actualOperationName = findActualRegistryOperationName(actual, operationName);
            assertOperationEquals(actualOperationName.applicationCode(),operationName.operationName(),
                actualOperationName.operationName());
        }
    }

    private static void assertOperationEquals(String applicationCode, List<String> expectedOperationName,
        List<String> actualOperationName) {
        for (String operationName : expectedOperationName) {
            if (!actualOperationName.contains(operationName)) {
                throw new RegistryOperationNameNotFoundException(applicationCode, operationName);
            }
        }
    }

    private static RegistryOperationName findActualRegistryOperationName(
        List<RegistryOperationName> actual, RegistryOperationName registryOperationName) {
        if (actual == null) {
            throw new ActualRegistryOperationNameEmptyException(registryOperationName);
        }

        for (RegistryOperationName operationName : actual) {
            if (operationName.applicationCode().equals(registryOperationName.applicationCode())) {
                return operationName;
            }
        }

        throw new RegistryOperationNamesOfApplicationNotFoundException(registryOperationName);
    }
}
