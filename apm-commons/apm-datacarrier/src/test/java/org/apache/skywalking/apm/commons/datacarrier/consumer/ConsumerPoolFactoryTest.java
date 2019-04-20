package org.apache.skywalking.apm.commons.datacarrier.consumer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author flycash
 * 2019-04-20
 */
public class ConsumerPoolFactoryTest {

    @Before
    public void createIfAbsent() throws Exception {
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator("my-test-pool", 10, 20);
        boolean firstCreated = ConsumerPoolFactory.INSTANCE.createIfAbsent("my-test-pool", creator);
        assertTrue(firstCreated);

        boolean secondCreated = ConsumerPoolFactory.INSTANCE.createIfAbsent("my-test-pool", creator);
        assertTrue(!secondCreated);
    }

    @Test
    public void get() {
        ConsumerPool consumerPool = ConsumerPoolFactory.INSTANCE.get("my-test-pool");
        assertNotNull(consumerPool);

        ConsumerPool notExist = ConsumerPoolFactory.INSTANCE.get("not-exists-pool");
        assertNull(notExist);
    }
}