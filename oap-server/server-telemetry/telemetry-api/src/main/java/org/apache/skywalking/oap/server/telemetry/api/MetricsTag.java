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

package org.apache.skywalking.oap.server.telemetry.api;

/**
 * Tag for the target metrics.
 * <p>
 * The tag values should be set in putting value phase.
 */
public class MetricsTag {
    public static final Keys EMPTY_KEY = new Keys();
    public static final Values EMPTY_VALUE = new Values();

    public static class Keys {
        private String[] keys;

        public Keys() {
            this.keys = new String[0];
        }

        public Keys(String... keys) {
            this.keys = keys;
        }

        public String[] getKeys() {
            return keys;
        }
    }

    public static class Values {
        private String[] values;

        public Values(Keys keys) {
            this.values = new String[0];
        }

        public Values(String... keys) {
            this.values = keys;
        }

        public String[] getValues() {
            return values;
        }
    }
}
