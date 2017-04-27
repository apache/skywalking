package com.a.eye.skywalking.collector.actor.selector;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class AbstractHashMessageTestCase {

    @Test
    public void testGetHashCode() {
        String key = "key";

        Impl impl = new Impl(key);
        Assert.assertEquals(key.hashCode(), impl.getHashCode());
    }

    class Impl extends AbstractHashMessage {
        public Impl(String key) {
            super(key);
        }
    }
}
