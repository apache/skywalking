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
