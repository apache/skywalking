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

package org.apache.skywalking.apm.collector.core.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class NumberFormatUtilsTest {

    @Test
    public void testRateNumberFormat() {
        Double rate = NumberFormatUtils.rateNumberFormat(12.1111);
        Assert.assertEquals(rate, Double.valueOf(12.11));

        rate = NumberFormatUtils.rateNumberFormat(12.1151);
        Assert.assertEquals(rate, Double.valueOf(12.12));

        rate = NumberFormatUtils.rateNumberFormat(12.1);
        Assert.assertEquals(rate, Double.valueOf(12.1));

        rate = NumberFormatUtils.rateNumberFormat(12.00);
        Assert.assertEquals(rate, Double.valueOf(12.00));

        rate = NumberFormatUtils.rateNumberFormat(4624.00);
        Assert.assertEquals(rate, Double.valueOf(4624.00));
    }
}
