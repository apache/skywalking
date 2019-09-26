package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import java.util.List;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryInstanceOfApplicationNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.RegistryInstanceSizeNotEqualsException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.RegistryInstance;

/**
 * Created by xin on 2017/7/15.
 */
public class InstanceAssert {
    public static void assertEquals(List<RegistryInstance> expected, List<RegistryInstance> actual) {

        if (expected == null) {
            return;
        }

        for (RegistryInstance instance : expected) {
            RegistryInstance actualInstance = getMatchApplication(actual, instance);
            try {
                ExpressParser.parse(actualInstance.expressValue()).assertValue(String.format("The registry instance of %s",
                    instance.applicationCode()), actualInstance.expressValue());
            } catch (ValueAssertFailedException e) {
                throw new RegistryInstanceSizeNotEqualsException(instance.applicationCode(), e);
            }
        }
    }

    private static RegistryInstance getMatchApplication(List<RegistryInstance> actual,
        RegistryInstance application) {
        for (RegistryInstance registryApplication : actual) {
            if (registryApplication.applicationCode().equals(application.applicationCode())) {
                return registryApplication;
            }
        }
        throw new RegistryInstanceOfApplicationNotFoundException(application.applicationCode());
    }
}
