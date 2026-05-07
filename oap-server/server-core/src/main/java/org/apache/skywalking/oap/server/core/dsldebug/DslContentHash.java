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

package org.apache.skywalking.oap.server.core.dsldebug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hex digest of a DSL source string. Threaded into every generated
 * rule's {@link GateHolder} so captured records carry the same byte-identity
 * as the runtime-rule engine's stored hash — UI clients can correlate a
 * capture with the exact rule revision that produced it.
 *
 * <p>Lives in {@code server-core} so static-loader paths (log-analyzer,
 * meter-analyzer) and the runtime-rule receiver can share one implementation
 * without cross-module dependency churn. The runtime-rule receiver carries
 * its own copy under {@code receiver.runtimerule.util.ContentHash}, scheduled
 * for consolidation in a follow-up.
 */
public final class DslContentHash {

    private DslContentHash() {
    }

    public static String sha256Hex(final String content) {
        if (content == null) {
            return "";
        }
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(64);
            for (final byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required by every JVM per the spec — this cannot happen.
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
