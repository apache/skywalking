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

package org.apache.skywalking.apm.collector.analysis.register.provider.register;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class IdAutoIncrementTestCase {

    @Test
    public void testIncrement() {
        int id = IdAutoIncrement.INSTANCE.increment(0, 0);
        Assert.assertEquals(-1, id);

        id = IdAutoIncrement.INSTANCE.increment(-1, -1);
        Assert.assertEquals(1, id);

        id = IdAutoIncrement.INSTANCE.increment(-1, 1);
        Assert.assertEquals(2, id);

        id = IdAutoIncrement.INSTANCE.increment(-1, 2);
        Assert.assertEquals(-2, id);

        id = IdAutoIncrement.INSTANCE.increment(-2, 2);
        Assert.assertEquals(3, id);
    }
}
