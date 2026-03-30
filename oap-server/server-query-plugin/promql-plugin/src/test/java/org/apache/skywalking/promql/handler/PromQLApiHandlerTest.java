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

package org.apache.skywalking.promql.handler;

import java.time.OffsetDateTime;
import org.apache.skywalking.oap.query.promql.handler.PromQLApiHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PromQLApiHandlerTest {

    @Test
    public void testRFC3339Parse() {
        String[] testCases = {
            "2026-01-20T03:27:18Z",
            "2026-01-20T03:27:18.0Z",
            "2026-01-20T03:27:18.000Z",
            "2026-01-20T03:27:18.123Z",
            "2026-01-20T11:27:18+08:00",
            "2026-01-20T11:27:18.0+08:00",
            "2026-01-20T11:27:18.123+08:00",
            "2026-01-20T11:27:18+0800",
            "2026-01-20T11:27:18.0+0800",
            "2026-01-20T11:27:18.123+0800",
            "2026-01-19T19:27:18-08:00",
            "2026-01-19T19:27:18.0-08:00",
            "2026-01-19T19:27:18.123-08:00",
            "2026-01-19T19:27:18-0800",
            "2026-01-19T19:27:18.0-0800",
            "2026-01-19T19:27:18.123-0800",
            "2026-01-20T03:27:18"
        };

        for (String str : testCases) {
            OffsetDateTime odt = OffsetDateTime.parse(str, PromQLApiHandler.RFC3339_FORMATTER);
            Assertions.assertEquals(1768879638, odt.toEpochSecond());
        }
    }
}
