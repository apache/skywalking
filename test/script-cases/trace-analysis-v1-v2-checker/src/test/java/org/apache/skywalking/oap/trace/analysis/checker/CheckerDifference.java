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

package org.apache.skywalking.oap.trace.analysis.checker;

/**
 * The behaviour the port changes by choice, each shown by fixtures of its own and checked for the exact change rather
 * than for equality.
 */
enum CheckerDifference {
    /**
     * A repeated tag key: the Java endpoint source builder kept the last value in a source's {@code originalTags}, the
     * rules keep the first, as {@code tag(key)} answers.
     */
    REPEATED_TAG_FIRST_VALUE,
    /**
     * A repeated tag key on a cache span: the Java cache processor's {@code Collectors.toMap} threw and failed the
     * segment, the rule analyzes the span.
     */
    REPEATED_CACHE_TAG_ANALYZED,
    /**
     * A bare host whose gateway {@code gateways.yml} lists at port 80: the listeners' exact lookup misses it, the shared
     * {@code isGateway} finds it.
     */
    BARE_HOST_GATEWAY
}
