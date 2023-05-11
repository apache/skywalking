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

package org.apache.skywalking.library.elasticsearch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.skywalking.library.elasticsearch.requests.factory.Codec;
import org.apache.skywalking.library.elasticsearch.requests.factory.RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.v6.V6RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.v6.codec.V6Codec;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.V78RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.V7RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.V81RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.codec.V78Codec;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.codec.V7Codec;

public final class ElasticSearchVersion {
    private final String distribution;
    private final int major;
    private final int minor;

    private final RequestFactory requestFactory;
    private final Codec codec;

    private ElasticSearchVersion(final String distribution, final int major, final int minor) {
        this.distribution = distribution;
        this.major = major;
        this.minor = minor;

        if (distribution.equalsIgnoreCase("OpenSearch")) {
            requestFactory = new V81RequestFactory(this);
            codec = V78Codec.INSTANCE;
            return;
        }

        if (distribution.equalsIgnoreCase("ElasticSearch")) {
            if (major == 6) { // 6.x
                requestFactory = new V6RequestFactory(this);
                codec = V6Codec.INSTANCE;
                return;
            }
            if (major == 7) {
                if (minor < 8) { // [7.0, 7.8)
                    requestFactory = new V7RequestFactory(this);
                    codec = V7Codec.INSTANCE;
                } else { // [7.8, 8.0)
                    requestFactory = new V78RequestFactory(this);
                    codec = V78Codec.INSTANCE;
                }
                return;
            }
            if (major == 8) {
                requestFactory = new V81RequestFactory(this);
                codec = V78Codec.INSTANCE;
                return;
            }
        }
        throw new UnsupportedOperationException("Unsupported version: " + this);
    }

    @Override
    public String toString() {
        return distribution + " " + major + "." + minor;
    }

    private static final Pattern REGEX = Pattern.compile("(\\d+)\\.(\\d+).*");

    public static ElasticSearchVersion of(String distribution, String version) {
        final Matcher matcher = REGEX.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Failed to parse version: " + version);
        }
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        return new ElasticSearchVersion(distribution, major, minor);
    }

    /**
     * Returns a {@link RequestFactory} that is responsible to compose correct requests according to
     * the syntax of specific {@link ElasticSearchVersion}.
     */
    public RequestFactory requestFactory() {
        return requestFactory;
    }

    /**
     * Returns a {@link Codec} to encode the requests and decode the response.
     */
    public Codec codec() {
        return codec;
    }
}
