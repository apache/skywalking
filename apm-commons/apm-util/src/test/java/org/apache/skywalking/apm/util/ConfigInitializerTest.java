/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

public class ConfigInitializerTest {
    @Test
    public void testInitialize() throws IllegalAccessException {
        Properties properties = new Properties();
        properties.put("Level1Object.STR_ATTR".toLowerCase(), "stringValue");
        properties.put("Level1Object.Level2Object.INT_ATTR".toLowerCase(), "1000");
        properties.put("Level1Object.Level2Object.LONG_ATTR".toLowerCase(), "1000");
        properties.put("Level1Object.Level2Object.BOOLEAN_ATTR".toLowerCase(), "true");
        properties.put("Level1Object.LIST_ATTR".toLowerCase(), "1,2,3");
        properties.put("Level1Object.LIST_EMPTY_ATTR".toLowerCase(), "");
        properties.put("Level1Object.Level2Object.ENUM_ATTR".toLowerCase(), "RED");

        ConfigInitializer.initialize(properties, TestPropertiesObject.class);

        Assert.assertEquals("stringValue", TestPropertiesObject.Level1Object.STR_ATTR);
        Assert.assertEquals(1000, TestPropertiesObject.Level1Object.Level2Object.INT_ATTR);
        Assert.assertEquals(1000L, TestPropertiesObject.Level1Object.Level2Object.LONG_ATTR);
        Assert.assertEquals(true, TestPropertiesObject.Level1Object.Level2Object.BOOLEAN_ATTR);
        Assert.assertArrayEquals(new String[] {}, TestPropertiesObject.Level1Object.LIST_EMPTY_ATTR.toArray());
        Assert.assertEquals(TestColorEnum.RED, TestPropertiesObject.Level1Object.Level2Object.ENUM_ATTR);
        //make sure that when descs is empty,toString() work right;
        Assert.assertEquals(new ConfigDesc().toString(), "");
    }

    @Test
    public void testInitializeWithUnsupportedConfig() throws IllegalAccessException {
        Properties properties = new Properties();
        properties.put("Level1Object.noExistAttr".toLowerCase(), "stringValue");

        ConfigInitializer.initialize(properties, TestPropertiesObject.class);

        Assert.assertNull(TestPropertiesObject.Level1Object.STR_ATTR);
    }

    @Before
    public void clear() {
        TestPropertiesObject.Level1Object.STR_ATTR = null;
        TestPropertiesObject.Level1Object.LIST_ATTR = null;
        TestPropertiesObject.Level1Object.Level2Object.INT_ATTR = 0;
        TestPropertiesObject.Level1Object.Level2Object.LONG_ATTR = 0;
        TestPropertiesObject.Level1Object.Level2Object.BOOLEAN_ATTR = false;
        TestPropertiesObject.Level1Object.Level2Object.ENUM_ATTR = null;
    }

    public static class TestPropertiesObject {
        public static class Level1Object {
            public static String STR_ATTR = null;
            public static List LIST_ATTR = null;
            public static List LIST_EMPTY_ATTR = null;

            public static class Level2Object {
                public static int INT_ATTR = 0;

                public static long LONG_ATTR;

                public static boolean BOOLEAN_ATTR;

                public static TestColorEnum ENUM_ATTR;
            }
        }
    }

    private enum TestColorEnum {
        RED, BLACK;
    }
}
