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

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The service names this OAP has seen spans from, so an address can be recognized as naming a service. Fed by every
 * analyzed span; read by the runtime's {@code alias} and {@code resolveAlias}, which treat a host that names a service
 * as that service and never as an address to register.
 *
 * <p>A grouped service, {@code shop::cart}, is also known by its short name, {@code cart}, the host its callers dial:
 * the group is OAP's naming, not the network's. A short name that two groups' services emitted under, or were read from
 * storage under, within a day names neither, since the host cannot say which one was dialed; once one of them has been
 * silent for a day, the short name names the other again.
 *
 * <p>Per OAP node and bounded. A name is forgotten a day after its last span or lookup, so a renamed or retired service
 * does not stay a service forever. When the node starts, the provider also records the services storage already holds,
 * so a restart does not forget a callee that emits only now and then. A cluster peer that never received a service's
 * spans does not know it: the exact names only delay the guard on that node, but a short name it knows from one group
 * alone would name that group's service for every group's callers, which is why {@link AliasRegistrar} asks the
 * caller's own group first and ranks the short name last.
 */
public class KnownServices implements Service {
    private static final long MAX_NAMES = 10_000L;
    private static final long FORGET_AFTER_NANOS = TimeUnit.DAYS.toNanos(1);
    /**
     * How stale a short name's member may get before a span of it writes its last-seen time again, so a busy
     * service does not write on every span.
     */
    private static final long REFRESH_AFTER_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Ticker ticker;

    /**
     * Whether a grouped name was ever recorded, so a fleet that groups nothing skips the grouped lookups.
     */
    private volatile boolean groupedServices;

    private final Cache<String, Boolean> names;

    /**
     * Names read from storage at start, whether or not a span recorded them too. Forgotten a day after the read
     * whatever the lookups, unlike {@link #names}: a lookup is no sign the service still emits, and a retired name dialed on and
     * on would otherwise stay a service, refusing the alias row of the service that took its address over.
     */
    private final Cache<String, Boolean> stored;

    /**
     * Short name to the grouped services emitting under it, each with the {@link Ticker} time of its last span, or of
     * the storage read at start for a service no span recorded since.
     */
    private final Cache<String, Map<String, Long>> byShortName;

    public KnownServices() {
        this(Ticker.systemTicker());
    }

    KnownServices(final Ticker ticker) {
        this.ticker = ticker;
        names = CacheBuilder.newBuilder()
                            .ticker(ticker)
                            .maximumSize(MAX_NAMES)
                            .expireAfterAccess(1, TimeUnit.DAYS)
                            .build();
        stored = CacheBuilder.newBuilder()
                             .ticker(ticker)
                             .maximumSize(MAX_NAMES)
                             .expireAfterWrite(1, TimeUnit.DAYS)
                             .build();
        byShortName = CacheBuilder.newBuilder()
                                  .ticker(ticker)
                                  .maximumSize(MAX_NAMES)
                                  .expireAfterAccess(1, TimeUnit.DAYS)
                                  .build();
    }

    /**
     * @param name a service that just emitted a span; empty names are ignored
     */
    public void record(final String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (names.getIfPresent(name) == null) {
            names.put(name, Boolean.TRUE);
        }
        recordShortName(name);
    }

    /**
     * Record a service storage holds, as known for a day after the read unless a span of it records it for real.
     *
     * @param name a stored service; empty names are ignored
     */
    public void recordStored(final String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        // Put whether or not a span recorded it already: reading names here would renew a name no span renewed.
        stored.put(name, Boolean.TRUE);
        recordShortName(name);
    }

    private void recordShortName(final String name) {
        final String shortName = shortNameOf(name);
        if (shortName == null) {
            return;
        }
        if (!groupedServices) {
            groupedServices = true;
        }
        // Read on every span, not only a service's first: the read keeps the short name alive as long as its
        // service emits, and a short name that expired or was evicted while the full name stayed is put back.
        Map<String, Long> members = byShortName.getIfPresent(shortName);
        if (members == null) {
            // Atomic, so two groups' first spans racing on one short name both land in one member map.
            members = byShortName.asMap().computeIfAbsent(shortName, k -> new ConcurrentHashMap<>());
        }
        final long now = ticker.read();
        final Long seen = members.get(name);
        if (seen == null || now - seen >= REFRESH_AFTER_NANOS) {
            members.put(name, now);
        }
    }

    /**
     * @return whether a grouped service has ever emitted spans to this node, or was read from storage
     */
    public boolean hasGroupedServices() {
        return groupedServices;
    }

    /**
     * @param name a host or a service name
     * @return whether a service of exactly that name has emitted spans, or was read from storage within a day
     */
    public boolean contains(final String name) {
        return name != null && !name.isEmpty()
            && (names.getIfPresent(name) != null || stored.getIfPresent(name) != null);
    }

    /**
     * @param host a host, or a service name
     * @return the service of exactly that name, else the one grouped service that emitted under that short name, or
     * was read from storage, within a day; null when neither, or when two groups' services did
     */
    public String nameOf(final String host) {
        if (contains(host)) {
            return host;
        }
        if (host == null || host.isEmpty()) {
            return null;
        }
        final Map<String, Long> members = liveMembers(host);
        if (members == null) {
            return null;
        }
        // One pass rather than size() then next(): a concurrent record or prune may change the map in between.
        String only = null;
        for (final String member : members.keySet()) {
            if (only != null) {
                return null;
            }
            only = member;
        }
        return only;
    }

    /**
     * @param shortName a short name, {@code cart} of {@code shop::cart}
     * @return whether a grouped service of any group emitted under it, or was read from storage, within a day, however
     * many groups share it
     */
    public boolean hasShortName(final String shortName) {
        if (shortName == null || shortName.isEmpty()) {
            return false;
        }
        final Map<String, Long> members = liveMembers(shortName);
        return members != null && !members.isEmpty();
    }

    /**
     * @return the grouped services under the short name that emitted, or were read from storage, within a day, the
     * others dropped from the map first; null when the short name is unknown
     */
    private Map<String, Long> liveMembers(final String shortName) {
        final Map<String, Long> members = byShortName.getIfPresent(shortName);
        if (members == null) {
            return null;
        }
        final long now = ticker.read();
        for (final Map.Entry<String, Long> member : members.entrySet()) {
            final Long seen = member.getValue();
            if (now - seen >= FORGET_AFTER_NANOS) {
                // Only if unchanged: a span of that member may have refreshed it since the read.
                members.remove(member.getKey(), seen);
            }
        }
        return members;
    }

    /**
     * @param service a service name
     * @return its group, {@code shop} of {@code shop::cart}; null for an ungrouped service, or one with nothing on
     * either side of the separator
     */
    public static String groupOf(final String service) {
        return shortNameOf(service) == null ? null : service.substring(0, service.indexOf(Const.DOUBLE_COLONS_SPLIT));
    }

    /**
     * @param service a service name
     * @return its short name, {@code cart} of {@code shop::cart}; null for an ungrouped service, or one with nothing
     * on either side of the separator
     */
    public static String shortNameOf(final String service) {
        if (service == null) {
            return null;
        }
        final int split = service.indexOf(Const.DOUBLE_COLONS_SPLIT);
        if (split <= 0 || split + Const.DOUBLE_COLONS_SPLIT.length() >= service.length()) {
            return null;
        }
        return service.substring(split + Const.DOUBLE_COLONS_SPLIT.length());
    }
}
