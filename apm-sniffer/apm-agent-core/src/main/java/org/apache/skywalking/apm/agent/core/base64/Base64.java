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

package org.apache.skywalking.apm.agent.core.base64;

import java.nio.charset.StandardCharsets;

/**
 * A wrapper of {@link java.util.Base64} with convenient conversion methods between {@code byte[]} and {@code String}
 */
public final class Base64 {
    private static final java.util.Base64.Decoder DECODER = java.util.Base64.getDecoder();
    private static final java.util.Base64.Encoder ENCODER = java.util.Base64.getEncoder();

    private Base64() {
    }

    public static String decode2UTFString(String in) {
        return new String(DECODER.decode(in), StandardCharsets.UTF_8);
    }

    public static String encode(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

}
