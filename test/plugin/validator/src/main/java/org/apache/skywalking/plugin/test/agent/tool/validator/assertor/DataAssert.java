package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Data;

public class DataAssert {
    private static Logger logger = LogManager.getLogger(DataAssert.class);

    public static void assertEquals(Data excepted, Data actual) {
        logger.info("excepted data:\n{}", new GsonBuilder().setPrettyPrinting().create().toJson(excepted));
        logger.info("actual data:\n{}", new GsonBuilder().setPrettyPrinting().create().toJson(actual));
        RegistryItemsAssert.assertEquals(excepted.registryItems(), actual.registryItems());
        SegmentItemsAssert.assertEquals(excepted.segmentItems(), actual.segmentItems());
        logger.info("{} assert successful.", "segment items");
    }
}
