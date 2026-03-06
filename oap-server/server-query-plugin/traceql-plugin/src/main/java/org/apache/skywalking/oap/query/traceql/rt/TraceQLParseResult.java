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

package org.apache.skywalking.oap.query.traceql.rt;

import lombok.Data;

/**
 * Result of parsing a TraceQL query expression.
 * Contains the extracted query parameters and any parse error information.
 */
@Data
public class TraceQLParseResult {

    /**
     * Extracted query parameters from the TraceQL expression.
     * Null if parsing failed.
     */
    private TraceQLQueryParams params;

    /**
     * Error message if parsing failed, null if successful.
     */
    private String errorInfo;

    /**
     * Returns true if parsing encountered an error.
     */
    public boolean hasError() {
        return errorInfo != null;
    }

    /**
     * Create a successful parse result with the given params.
     */
    public static TraceQLParseResult of(TraceQLQueryParams params) {
        TraceQLParseResult result = new TraceQLParseResult();
        result.setParams(params);
        return result;
    }

    /**
     * Create a failed parse result with an error message.
     */
    public static TraceQLParseResult error(String errorInfo) {
        TraceQLParseResult result = new TraceQLParseResult();
        result.setErrorInfo(errorInfo);
        return result;
    }
}
