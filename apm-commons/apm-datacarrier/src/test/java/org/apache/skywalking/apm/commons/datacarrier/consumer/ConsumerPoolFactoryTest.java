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

package org.apache.skywalking.apm.commons.datacarrier.consumer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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