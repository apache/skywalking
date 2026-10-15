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

package org.apache.skywalking.oap.server.library.util;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectUtilsLeadingCommaTest {
    @Test
    void preservesAllAddressesAfterLeadingComma() throws ConnectStringParseException {
        final List<Address> addresses = ConnectUtils.parse(",10.0.0.1:8500,10.0.0.2:8501,10.0.0.3:8502,");
        assertEquals(3, addresses.size());
        for (int i = 0; i < addresses.size(); i++) {
            assertEquals("10.0.0." + (i + 1), addresses.get(i).getHost());
            assertEquals(8500 + i, addresses.get(i).getPort());
        }
    }

    @Test
    void rejectsEmptyAddressInsideListWithLeadingComma() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse(",localhost:8500,,localhost:8501"));
    }
}
