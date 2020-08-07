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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser;

import org.apache.skywalking.oap.server.core.Const;

/**
 * Reserved keys of the span. The backend analysis the metrics according the existed tags.
 */
public class SpanTags {
    public static final String STATUS_CODE = "status_code";

    public static final String DB_STATEMENT = "db.statement";

    public static final String DB_TYPE = "db.type";

    /**
     * Tag, x-le(extension logic endpoint) series tag. Value is JSON format.
     * <pre>
     * {
     *   "name": "GraphQL-service",
     *   "latency": 100,
     *   "status": true
     * }
     * </pre>
     *
     * Also, could use value to indicate this local span is representing a logic endpoint.
     * <pre>
     * {
     *   "logic-span": true
     * }
     * </pre>
     */
    public static final String LOGIC_ENDPOINT = "x-le";

    /**
     * SkyWalking Java Agent provides the recommended tag keys for other language agents or SDKs. This field declare the
     * recommended keys should be searchable. User could override this through {@link
     * org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig#setSearchableTagKeys(String)} or the
     * system environment variable defined in the application.yml.
     */
    public static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
        Const.COMMA,
        "http.method",
        "status_code",
        "db.type",
        "db.instance",
        "mq.queue",
        "mq.topic",
        "mq.broker"
        );
}
