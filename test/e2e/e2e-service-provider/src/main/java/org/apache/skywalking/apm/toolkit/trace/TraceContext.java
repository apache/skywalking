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

package org.apache.skywalking.apm.toolkit.trace;

import java.util.Optional;

/**
 * Try to access the sky-walking tracer context. The context is not existed, always. only the middleware, component, or
 * rpc-framework are supported in the current invoke stack, in the same thread, the context will be available.
 * <p>
 */
public class TraceContext {

    /**
     * Try to get the traceId of current trace context.
     *
     * @return traceId, if it exists, or empty {@link String}.
     */
    public static String traceId() {
        return "";
    }

    /**
     * Try to get the custom value from trace context.
     *
     * @return custom data value.
     */
    public static Optional<String> getCorrelation(String key) {
        return Optional.empty();
    }

    /**
     * Put the custom key/value into trace context.
     *
     * @return previous value if it exists.
     */
    public static Optional<String> putCorrelation(String key, String value) {
        return Optional.empty();
    }

}
