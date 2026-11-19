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

package org.apache.skywalking.library.elasticsearch.requests.factory;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

/**
 * Escapes a value the client puts into a request path as one segment, such as a document id. The HTTP client does
 * not escape path params, so an id holding {@code /}, {@code ?}, {@code #} or {@code %} would change the path: a
 * runtime rule is named like {@code aws-gateway/gateway-service}, and a service id, which some documents are keyed
 * by, is Base64 and can hold {@code /}.
 *
 * <p>A {@code +} reaches the server as it is, escaped or not, as the HTTP client decodes {@code %2B} in a path.
 * Elasticsearch 7.4 and later and OpenSearch read it as {@code +}; Elasticsearch 6 reads it as a space.
 */
public final class PathSegments {
    private static final Escaper ESCAPER = UrlEscapers.urlPathSegmentEscaper();

    private PathSegments() {
    }

    public static String escape(final String segment) {
        return ESCAPER.escape(segment);
    }
}
