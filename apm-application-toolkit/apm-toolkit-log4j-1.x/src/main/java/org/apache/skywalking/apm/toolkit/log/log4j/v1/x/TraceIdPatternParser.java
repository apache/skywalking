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

package org.apache.skywalking.apm.toolkit.log.log4j.v1.x;

import org.apache.log4j.helpers.PatternParser;

/**
 * Base on '%T', use {@link TraceIdPatternConverter} to convert the '%T' to traceId or '%T{SW_CTX}' to SkyWalking context.
 * <p>
 */
public class TraceIdPatternParser extends PatternParser {
    private static final String SKYWALKING_CONTEXT_OPTION = "SW_CTX";

    public TraceIdPatternParser(String pattern) {
        super(pattern);
    }

    @Override
    protected void finalizeConverter(char c) {
        if ('T' == c) {
            String option = super.extractOption();
            if (option != null && option.equals(SKYWALKING_CONTEXT_OPTION)) {
                addConverter(new SkyWalkingContextPatternConverter());
            } else {
                addConverter(new TraceIdPatternConverter());
            }
        } else {
            super.finalizeConverter(c);
        }
    }
}
