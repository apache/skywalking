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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The service names this OAP has seen spans from, so an address can be recognized as naming a service. Fed by
 * every analyzed span; read by the runtime's {@code alias} and {@code resolveAlias}, which treat a host that
 * names a service as that service and never as an address to register.
 *
 * <p>A grouped service, {@code shop::cart}, is also known by its short name, {@code cart}, the host its callers
 * dial: the group is OAP's naming, not the network's. A short name that two groups share names neither, since the
 * host cannot say which one was dialed.
 *
 * <p>Per OAP node and bounded. A name is forgotten a day after its last span or lookup, so a renamed or retired
 * service does not stay a service forever; a cluster peer that never received a service's spans does not know
 * it, which only delays the guard on that node until it does.
 */
public class KnownServices implements Service {
    private static final long MAX_NAMES = 10_000L;
    /**
     * Stands in for the service of a short name two groups share.
     */
    private static final String AMBIGUOUS = "";

    private final Cache<String, Boolean> names = CacheBuilder.newBuilder()
                                                             .maximumSize(MAX_NAMES)
                                                             .expireAfterAccess(1, TimeUnit.DAYS)
                                                             .build();

    private final Cache<String, String> byShortName = CacheBuilder.newBuilder()
                                                                  .maximumSize(MAX_NAMES)
                                                                  .expireAfterAccess(1, TimeUnit.DAYS)
                                                                  .build();

    /**
     * @param name a service that just emitted a span; empty names are ignored
     */
    public void record(final String name) {
        if (name == null || name.isEmpty() || names.getIfPresent(name) != null) {
            return;
        }
        names.put(name, Boolean.TRUE);
        final int split = name.indexOf(Const.DOUBLE_COLONS_SPLIT);
        if (split > 0 && split + Const.DOUBLE_COLONS_SPLIT.length() < name.length()) {
            final String shortName = name.substring(split + Const.DOUBLE_COLONS_SPLIT.length());
            final String seen = byShortName.getIfPresent(shortName);
            byShortName.put(shortName, seen == null || seen.equals(name) ? name : AMBIGUOUS);
        }
    }

    /**
     * @param name a host or a service name
     * @return whether a service of exactly that name has emitted spans
     */
    public boolean contains(final String name) {
        return name != null && !name.isEmpty() && names.getIfPresent(name) != null;
    }

    /**
     * @param host a host, or a service name
     * @return the service of exactly that name, else the one grouped service whose short name it is; null when
     * neither, or when two groups share the short name
     */
    public String nameOf(final String host) {
        if (contains(host)) {
            return host;
        }
        if (host == null || host.isEmpty()) {
            return null;
        }
        final String grouped = byShortName.getIfPresent(host);
        return grouped == null || AMBIGUOUS.equals(grouped) ? null : grouped;
    }
}
