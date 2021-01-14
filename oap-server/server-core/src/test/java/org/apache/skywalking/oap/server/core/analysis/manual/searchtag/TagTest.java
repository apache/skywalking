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
 */

package org.apache.skywalking.oap.server.core.analysis.manual.searchtag;

import org.junit.Assert;
import org.junit.Test;

public class TagTest {
    @Test
    public void testEqual() {
        final Tag tag = new Tag("tag1", "value1");
        final Tag tag1 = new Tag("tag1", "value2");
        final Tag tag2 = new Tag("tag2", "value3");
        final Tag tag3 = new Tag("tag1", "value1");
        Assert.assertEquals(tag, tag3);
        Assert.assertNotEquals(tag, tag1);
        Assert.assertNotEquals(tag, tag2);
    }
}
