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

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryApplicationNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryApplicationSizeNotEqualsException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.RegistryApplication;

public class ApplicationAssert {
    public static void assertEquals(List<RegistryApplication> expected,
        List<RegistryApplication> actual) {

        if (expected == null) {
            return;
        }

        for (RegistryApplication application : expected) {
            RegistryApplication actualApplication = getMatchApplication(actual, application);
            try {
                ExpressParser.parse(application.expressValue()).assertValue("registry application", actualApplication.expressValue());
            } catch (ValueAssertFailedException e) {
                throw new RegistryApplicationSizeNotEqualsException(application.applicationCode(), e);
            }
        }
    }

    private static RegistryApplication getMatchApplication(List<RegistryApplication> actual,
        RegistryApplication application) {
        for (RegistryApplication registryApplication : actual) {
            if (registryApplication.applicationCode().equals(application.applicationCode())) {
                return registryApplication;
            }
        }
        throw new RegistryApplicationNotFoundException(application.applicationCode());
    }
}
