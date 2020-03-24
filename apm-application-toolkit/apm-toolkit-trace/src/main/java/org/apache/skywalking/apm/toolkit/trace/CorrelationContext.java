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

package org.apache.skywalking.apm.toolkit.trace;

import java.util.Optional;

/**
 * CorrelationContext is the interactive API for end user to put/set custom data.
 */
public class CorrelationContext {

    /**
     * Try to get the custom value from trace context.
     *
     * @return custom data value.
     */
    public static Optional<String> get(String key) {
        return Optional.empty();
    }

    /**
     * Setting the custom key/value into trace context.
     *
     * @return previous value if it exists.
     */
    public static Optional<String> set(String key, String value) {
        return Optional.empty();
    }

}
