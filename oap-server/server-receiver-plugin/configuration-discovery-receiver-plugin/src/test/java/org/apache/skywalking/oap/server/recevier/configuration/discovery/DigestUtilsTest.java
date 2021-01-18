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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Assert;
import org.junit.Test;

public class DigestUtilsTest {
    @Test
    public void testMd5Hex() {
        String text =
            "configurations:\n" +
                "  serviceA:\n" +
                "    trace.sample_rate: 1000\n" +
                "    trace.ignore_path: /api/seller/seller/*\n" +
                "  serviceB:\n" +
                "    trace.sample_rate: 1000\n" +
                "    trace.ignore_path: /api/seller/seller/*\n";
        String md5Hex = DigestUtils.md5Hex(text);
        Assert.assertEquals("d52342af66661d0e72e5f5caf6457f35", md5Hex);

        String text1 = text +
            "  serviceA:\n" +
            "    trace.sample_rate: 1000\n" +
            "    trace.ignore_path: /api/seller/seller/*\n";
        Assert.assertNotEquals("d52342af66661d0e72e5f5caf6457f35", DigestUtils.md5Hex(text1));
    }
}
