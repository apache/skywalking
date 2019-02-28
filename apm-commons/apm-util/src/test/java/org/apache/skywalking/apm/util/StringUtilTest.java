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
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/27.
 */
public class StringUtilTest {
    @Test
    public void testIsEmpty() {
        Assert.assertTrue(StringUtil.isEmpty(null));
        Assert.assertTrue(StringUtil.isEmpty(""));
        Assert.assertFalse(StringUtil.isEmpty("   "));
        Assert.assertFalse(StringUtil.isEmpty("A String"));
    }

    @Test
    public void testJoin() {
        Assert.assertNull(StringUtil.join('.'));
        Assert.assertEquals("Single part.", StringUtil.join('.', "Single part."));
        Assert.assertEquals("part1.part2.p3", StringUtil.join('.', "part1", "part2", "p3"));
    }
}
