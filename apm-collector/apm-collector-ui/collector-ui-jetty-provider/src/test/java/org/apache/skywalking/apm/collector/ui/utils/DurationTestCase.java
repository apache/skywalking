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

package org.apache.skywalking.apm.collector.ui.utils;

import org.junit.Assert;

/**
 * @author peng-yongsheng
 */
public class DurationTestCase {

    @org.junit.Test
    public void test() {
        Assert.assertEquals(true, expression(80, 150));
        Assert.assertEquals(true, expression(80, 250));
        Assert.assertEquals(true, expression(150, 250));
        Assert.assertEquals(true, expression(120, 180));
        Assert.assertEquals(false, expression(70, 90));
        Assert.assertEquals(false, expression(250, 300));
    }

    private boolean expression(int start, int end) {
        int register = 100;
        int heart = 200;

        return (heart > end && register <= end) || (register <= end && heart >= start);
    }
}
