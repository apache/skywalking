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

package org.apache.skywalking.apm.plugin.customize.util;

import org.junit.Assert;
import org.junit.Test;

public class CustomizeUtilTest {

    @Test
    public void testClassForName() throws ClassNotFoundException {
        Assert.assertTrue(CustomizeUtil.isJavaClass("boolean.class"));
        Assert.assertTrue(CustomizeUtil.isJavaClass("char.class"));
        Assert.assertTrue(CustomizeUtil.getJavaClass("byte.class") == byte.class);
        Assert.assertTrue(CustomizeUtil.getJavaClass("short.class") == short.class);
        Assert.assertTrue(CustomizeUtil.getJavaClass("int.class") == int.class);
        Assert.assertTrue(CustomizeUtil.getJavaClass("long.class") == long.class);
        Assert.assertTrue(CustomizeUtil.getJavaClass("float.class") == float.class);
    }
}
