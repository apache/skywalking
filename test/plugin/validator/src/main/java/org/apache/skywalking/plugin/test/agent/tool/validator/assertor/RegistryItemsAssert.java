package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.RegistryItems;

public class RegistryItemsAssert {
    private static Logger logger = LogManager.getLogger(RegistryItemsAssert.class);

    public static void assertEquals(RegistryItems excepted, RegistryItems actual) {
        ApplicationAssert.assertEquals(excepted.applications(), actual.applications());
        logger.info("{} assert successful.", "registry applications");
        InstanceAssert.assertEquals(excepted.instances(), actual.instances());
        logger.info("{} assert successful.", "registry instances");
        OperationNameAssert.assertEquals(excepted.operationNames(), actual.operationNames());
        logger.info("{} assert successful.", "registry operation name");
    }
}
