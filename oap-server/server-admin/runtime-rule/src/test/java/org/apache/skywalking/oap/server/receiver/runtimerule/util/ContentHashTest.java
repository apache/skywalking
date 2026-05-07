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

package org.apache.skywalking.oap.server.receiver.runtimerule.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ContentHashTest {

    @Test
    void nullContentReturnsEmptyString() {
        // Null handling is deliberate — the dslManager represents "no content" as "" so state
        // maps can carry a non-null hash field for bundles that never compiled.
        assertEquals("", ContentHash.sha256Hex(null));
    }

    @Test
    void emptyContentHasDefinedHash() {
        // Known SHA-256 of the empty string, documented in the NIST FIPS 180-4 examples. Locked
        // down as a canary — if the algorithm selection or encoding drifts, this catches it.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContentHash.sha256Hex(""));
    }

    @Test
    void knownVectorMatches() {
        // RFC 6234 test vector for "abc" — cross-implementation reference.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ContentHash.sha256Hex("abc"));
    }

    @Test
    void sameContentHashesIdentically() {
        final String yaml = "metricPrefix: demo\nmetricsRules:\n  - name: r\n    exp: m.sum([])";
        assertEquals(ContentHash.sha256Hex(yaml), ContentHash.sha256Hex(yaml));
    }

    @Test
    void whitespaceAndCaseChangesProduceDifferentHashes() {
        // Byte-identity is the whole point — any diff, however trivial, is a different bundle.
        assertNotEquals(
            ContentHash.sha256Hex("name: x"),
            ContentHash.sha256Hex("name:  x"));
        assertNotEquals(
            ContentHash.sha256Hex("Name: x"),
            ContentHash.sha256Hex("name: x"));
    }

    @Test
    void hashIsAlways64HexChars() {
        final String hash = ContentHash.sha256Hex("payload");
        assertEquals(64, hash.length());
        for (int i = 0; i < hash.length(); i++) {
            final char c = hash.charAt(i);
            final boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) {
                throw new AssertionError("non-hex char at " + i + ": " + c);
            }
        }
    }
}
