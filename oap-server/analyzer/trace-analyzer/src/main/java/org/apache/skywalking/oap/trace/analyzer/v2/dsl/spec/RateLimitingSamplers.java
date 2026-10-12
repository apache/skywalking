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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The {@code rateLimit} counters of every rule, keyed by the id a {@code rateLimit("id")} block names, so two rules
 * that name one id share one budget. The first rule to use an id sets its {@code rpm}.
 *
 * <p>An id can be interpolated per span, {@code "slow-sql-${meta.service}"}, so the set is bounded and an id not
 * used for ten minutes is forgotten; a forgotten id starts a fresh minute when it comes back.
 */
public class RateLimitingSamplers implements Service {
    private static final long MAX_IDS = 10_000L;

    private final Cache<String, RateLimitingSampler> samplers = CacheBuilder.newBuilder()
                                                                         .maximumSize(MAX_IDS)
                                                                         .expireAfterAccess(10, TimeUnit.MINUTES)
                                                                         .build();

    /**
     * @param id  the counter's id, already interpolated
     * @param rpm the budget per minute, used when the id is new
     * @return the counter for the id
     */
    public RateLimitingSampler of(final String id, final int rpm) {
        final RateLimitingSampler existing = samplers.getIfPresent(id);
        if (existing != null) {
            return existing;
        }
        return samplers.asMap().computeIfAbsent(id, k -> new RateLimitingSampler(rpm));
    }
}
