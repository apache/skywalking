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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ByteUtilTest {
    @Test
    public void testConvertDoubleAndBackOnce() {
        double pi = 3.14159;
        byte[] data = ByteUtil.double2Bytes(pi);
        Assertions.assertEquals(8, data.length);
        Assertions.assertEquals(pi, ByteUtil.bytes2Double(data), 0.00001);
    }

    @Test
    public void testConvertDoubleAndBackTwice() {
        double pi = 3.14159;
        byte[] binaryPI = ByteUtil.double2Bytes(pi);
        Assertions.assertEquals(8, binaryPI.length);
        Assertions.assertEquals(pi, ByteUtil.bytes2Double(binaryPI), 0.00001);
        double e = 2.71828;
        byte[] binaryE = ByteUtil.double2Bytes(e);
        Assertions.assertEquals(8, binaryE.length);
        Assertions.assertEquals(e, ByteUtil.bytes2Double(binaryE), 0.00001);
    }
}
