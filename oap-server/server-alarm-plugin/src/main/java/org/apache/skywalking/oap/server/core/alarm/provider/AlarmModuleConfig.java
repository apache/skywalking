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

package org.apache.skywalking.oap.server.core.alarm.provider;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

/**
 * Settings under {@code alarm.default} in {@code application.yml}.
 */
@Getter
@Setter
public class AlarmModuleConfig extends ModuleConfig {
    public static final long DEFAULT_HTTP_TIMEOUT_SECONDS = 12;

    /**
     * Deadline (seconds) of each HTTP request an HTTP-based hook makes, covering connect, waiting for the
     * response and reading the response body. Values {@code <= 0} fall back to
     * {@link #DEFAULT_HTTP_TIMEOUT_SECONDS}. The gRPC hook does not use it.
     */
    private long httpTimeout = DEFAULT_HTTP_TIMEOUT_SECONDS;
}
