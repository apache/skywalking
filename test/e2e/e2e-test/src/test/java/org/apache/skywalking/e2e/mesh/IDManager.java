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

package org.apache.skywalking.e2e.mesh;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class IDManager {
    public static class ServiceID {

        public static ServiceIDDefinition analysisId(String id) {
            final String[] strings = id.split("\\.");
            if (strings.length != 2) {
                throw new RuntimeException("Can't split service id into 2 parts, " + id);
            }
            return new ServiceIDDefinition(
                decode(strings[0]),
                Integer.parseInt(strings[1]) == 1
            );
        }

        @RequiredArgsConstructor
        @Getter
        @EqualsAndHashCode
        public static class ServiceIDDefinition {
            private final String name;

            private final boolean isReal;
        }
    }

    /**
     * @param base64text Base64 encoded UTF-8 string
     * @return normal literal string
     */
    private static String decode(String base64text) {
        return new String(Base64.getDecoder().decode(base64text), StandardCharsets.UTF_8);
    }
}
