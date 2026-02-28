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

package org.apache.skywalking.oap.log.analyzer.dsl;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DSLV2Test {

    @Test
    void ofThrowsWhenManifestMissing() {
        // No META-INF/lal-expressions.txt on test classpath
        assertThrows(ModuleStartException.class,
            () -> DSL.of(null, null, "filter { json {} sink {} }"));
    }

    @Test
    void sha256Deterministic() {
        final String input = "filter { json {} sink {} }";
        final String hash1 = DSL.sha256(input);
        final String hash2 = DSL.sha256(input);
        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    void sha256DifferentInputsDifferentHashes() {
        final String hash1 = DSL.sha256("filter { json {} sink {} }");
        final String hash2 = DSL.sha256("filter { text {} sink {} }");
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2);
    }

    private static void assertNotEquals(final String a, final String b) {
        if (a.equals(b)) {
            throw new AssertionError("Expected different values but got: " + a);
        }
    }
}
