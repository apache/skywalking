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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import java.util.Optional;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;

public interface LogSinkListener {
    /**
     * The last step of the sink process. Typically, the implementations forward the results to the source
     * receiver.
     */
    void build();

    /**
     * Parse the raw data from the probe.
     * @return {@code this} for chaining.
     */
    LogSinkListener parse(LogData.Builder logData, Optional<Object> extraLog);

    /**
     * Parse the raw data from the probe with access to the execution context.
     * Implementations can use the context to apply output fields or other
     * per-execution state to the sink output.
     * @return {@code this} for chaining.
     */
    default LogSinkListener parse(final LogData.Builder logData, final Optional<Object> extraLog,
                                  final ExecutionContext ctx) {
        return parse(logData, extraLog);
    }
}
