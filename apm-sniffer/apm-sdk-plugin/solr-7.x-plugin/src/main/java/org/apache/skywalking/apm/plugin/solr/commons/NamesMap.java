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

package org.apache.skywalking.apm.plugin.solr.commons;

import org.apache.solr.handler.component.ShardRequest;

public class NamesMap {
    private static final String[] PURPOSENAMES = {
        "PRIVATE",
        "GET_TERM_DFS",
        "GET_TOP_IDS",
        "REFINE_TOP_IDS",
        "GET_FACETS",
        "REFINE_FACETS",
        "GET_FIELDS",
        "GET_HIGHLIGHTS",
        "GET_DEBUG",
        "GET_STATS",
        "GET_TERMS",
        "GET_TOP_GROUPS",
        "GET_MLT_RESULTS",
        "REFINE_PIVOT_FACETS",
        "SET_TERM_STATS",
        "GET_TERM_STATS"
    };

    public static String getStagName(int stage) {
        switch (stage) {
            case 0:
                return "START";
            case 1000:
                return "PARSE_QUERY";
            case 1500:
                return "TOP_GROUPS";
            case 2000:
                return "EXECUTE_QUERY";
            case 3000:
                return "GET_FIELDS";
            case Integer.MAX_VALUE:
                return "DONE";
            default:
                return "UNKNOWN";
        }
    }

    public static String gePurposeName(int purpose) {
        if (purpose < ShardRequest.PURPOSE_PRIVATE || purpose > ShardRequest.PURPOSE_GET_TERM_STATS)
            return "PENDING";
        return PURPOSENAMES[Integer.numberOfTrailingZeros(purpose)];
    }

}