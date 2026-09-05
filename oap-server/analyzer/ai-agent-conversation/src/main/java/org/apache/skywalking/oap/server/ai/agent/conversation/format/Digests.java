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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The digests the Sessionizer writes, reproduced so a stored file verifies and a chain checks.
 */
public final class Digests {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Digests() {
    }

    /**
     * @param bytes the bytes
     * @return lowercase hex sha256 of the bytes, the file digest on the wire and on every closing line
     */
    public static String sha256Hex(final byte[] bytes) {
        return hex(sha256().digest(bytes));
    }

    /**
     * A round's <code>input_digest</code>: the previous round's input digest hashed with the digests of the files
     * the round newly consumed, sorted, each preceded by a zero byte. Empty previous for round 1.
     *
     * @param previous the previous round's input digest, or empty
     * @param added    the digests of the files in the round's window
     * @return the chained digest
     */
    public static String chainInputDigest(final String previous, final List<String> added) {
        final List<String> sorted = new ArrayList<>(added);
        Collections.sort(sorted);
        final MessageDigest md = sha256();
        md.update(previous.getBytes(StandardCharsets.UTF_8));
        for (final String digest : sorted) {
            md.update((byte) 0);
            md.update(digest.getBytes(StandardCharsets.UTF_8));
        }
        return hex(md.digest());
    }

    /**
     * @return a fresh sha256 digest
     */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * @param digest the digest bytes
     * @return lowercase hex
     */
    public static String hex(final byte[] digest) {
        final char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            final int b = digest[i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(out);
    }

    /**
     * @param body the file bytes
     * @return how many lines the body has, counting newline terminators, the way <code>asz.lines</code> counts
     */
    public static int countLines(final byte[] body) {
        int n = 0;
        for (final byte b : body) {
            if (b == '\n') {
                n++;
            }
        }
        return n;
    }
}
