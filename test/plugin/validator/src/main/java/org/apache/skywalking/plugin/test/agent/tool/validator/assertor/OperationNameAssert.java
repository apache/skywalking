/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
