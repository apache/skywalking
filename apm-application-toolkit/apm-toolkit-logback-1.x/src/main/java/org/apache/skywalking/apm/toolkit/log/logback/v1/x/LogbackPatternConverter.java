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

package org.apache.skywalking.apm.toolkit.log.logback.v1.x;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class LogbackPatternConverter extends ClassicConverter {
    /**
     * As default, return "TID: N/A" to the output message, if sky-walking agent in active mode, return the real traceId
     * in the recent Context, if existed.
     *
     * @param iLoggingEvent the event
     * @return the traceId: N/A, empty String, or the real traceId.
     */
    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return "TID: N/A";
    }
}
