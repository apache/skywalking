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

package org.apache.skywalking.oap.server.core.analysis.metrics.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExactInMatchTest {

    @Test
    public void stringsKeepTheirQuotes() {
        final ExactInMatch in = new ExactInMatch();
        assertTrue(in.match("\"v1\"", new Object[] {"x", "\"v1\""}));
        assertFalse(in.match("v1", new Object[] {"\"v1\""}));
        assertFalse(in.match("\"v1\"", new Object[] {"v1"}));
        assertTrue(in.match("\"", new Object[] {"\""}));
        assertFalse(in.match(null, new Object[] {"v1"}));
    }
}
