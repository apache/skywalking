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

import java.util.function.Predicate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;

/**
 * Writes network address alias rows for the analyzer. The rule's {@code alias} statement and {@link SpanPairing}
 * both come through here, so one guard covers both: an address whose host, or first DNS label, names a service
 * other than the one it would represent is refused. A proxy that keeps the {@code Host} header hands its own
 * address to the service behind it, and a learned pair carries the same address when a browser dialed the proxy;
 * either way the row would pull every edge to the proxy onto the service behind it.
 *
 * <p>As on the agent path, an address listed in {@code gateways.yml} is never aliased, and otherwise the last write
 * wins. An uninstrumented proxy that passes its callers' address through to the service behind it belongs in that
 * file; one that emits its own spans keeps its address through the service-name check above.
 */
@Slf4j
public class AliasRegistrar {
    /**
     * The port {@code gateways.yml} assumes for an instance that names none.
     */
    private static final String GATEWAY_DEFAULT_PORT = ":80";

    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final KnownServices knownServices;
    /**
     * Whether an address is listed in {@code gateways.yml}; absent when the agent analyzer is not loaded.
     */
    @Setter
    private Predicate<String> gateways;

    public AliasRegistrar(final SourceReceiver sourceReceiver,
                          final NamingControl namingControl,
                          final KnownServices knownServices) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
        this.knownServices = knownServices;
    }

    /**
     * Register the address as an alias of the service, the row the agent path derives from the {@code sw8}
     * header's peer address.
     *
     * @param address         the address as the caller spelled it
     * @param service         the service it represents, unformatted
     * @param normal          the service id's {@code normal} flag, as its own metrics carry it
     * @param instance        the service instance, or empty
     * @param timestampMillis the span's end, which picks the row's minute bucket
     * @return whether a row was written; false for an empty address or service, or when a guard refused it
     */
    public boolean register(final String address, final String service, final boolean normal,
                            final String instance, final long timestampMillis) {
        if (address == null || address.isEmpty() || sourceReceiver == null) {
            return false;
        }
        final String formatted = namingControl.formatServiceName(service);
        if (formatted == null || formatted.isEmpty()) {
            return false;
        }
        final String host = hostOf(address);
        final String named = serviceNamedBy(host, service);
        if (named != null && !named.equals(service) && !named.equals(formatted)) {
            log.debug("Alias {} for {} skipped: the address names the service {}", address, formatted, named);
            return false;
        }
        if (named == null) {
            // Any group's service by the host's first label, however many groups share it and whether or not the host
            // is qualified, makes the address that service's, a proxy's that keeps the Host header. The callee's own
            // short name excepted, taken from the unformatted name since the naming rules may cut a long one while
            // spans record its short name uncut: another group's cart is the same workload deployed elsewhere, and
            // refusing on it would keep the callee's address from ever being registered.
            // The whole host counts too, for a short name that holds a dot itself, edge.proxy of infra::edge.proxy.
            final String own = shortNameOf(service);
            final String label = firstLabelOf(host);
            final String shortName = !label.equals(own) && knownServices.hasShortName(label) ? label
                : !host.equals(label) && !host.equals(own) && knownServices.hasShortName(host) ? host : null;
            if (shortName != null) {
                log.debug("Alias {} for {} skipped: the address names a grouped service {}",
                          address, formatted, shortName);
                return false;
            }
        }
        if (isGateway(address)) {
            log.debug("Alias {} for {} skipped: the address is an uninstrumented gateway", address, formatted);
            return false;
        }
        final NetworkAddressAliasSetup setup = new NetworkAddressAliasSetup();
        setup.setAddress(address);
        setup.setRepresentService(formatted);
        setup.setRepresentServiceNormal(normal);
        setup.setRepresentServiceInstance(namingControl.formatInstanceName(instance));
        setup.setTimeBucket(TimeBucket.getMinuteTimeBucket(timestampMillis));
        sourceReceiver.receive(setup);
        return true;
    }

    /**
     * @param address {@code host} or {@code host:port}
     * @return whether the address is listed in {@code gateways.yml}; a bare host is matched at port 80, as the agent
     * analyzer lists an instance that names no port. Always false when the agent analyzer is not loaded.
     */
    public boolean isGateway(final String address) {
        if (gateways == null || address == null || address.isEmpty()) {
            return false;
        }
        return gateways.test(address)
            || (hostOf(address).equals(address.trim()) && gateways.test(address.trim() + GATEWAY_DEFAULT_PORT));
    }

    /**
     * The known service a host names for this caller, resolved as Kubernetes DNS resolves a Service name: a host
     * qualified with a group, {@code cart.outlet} or {@code cart.outlet.svc.cluster.local}, names
     * {@code outlet::cart}; a bare host names the service of that name in the caller's own group, so {@code cart}
     * dialed by {@code shop::frontend} is {@code shop::cart}; else the whole host, or its first DNS label, is an
     * ungrouped service's name. A qualified host is never resolved in the caller's group by its first label, since it
     * says which group it means; only a service of the caller's group named exactly the whole host, a short name that
     * holds a dot such as {@code checkout.api}, matches it there. Only a service that has emitted spans to this node, or was read from storage when it started, see
     * {@link KnownServices#recordStored}, is ever named.
     *
     * @param host   a host name
     * @param caller the service on this side: the CLIENT span's service naming its peer, or the SERVER span's own
     *               service registering the address it was dialed at
     * @return the service the host names; null when none does, which leaves it to the alias cache, then to
     * {@link #serviceByShortName}
     */
    public String serviceNamedBy(final String host, final String caller) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        final String first = firstLabelOf(host);
        final boolean qualified = first.length() < host.length();
        if (knownServices.hasGroupedServices()) {
            if (qualified) {
                final int next = host.indexOf('.', first.length() + 1);
                final String second = host.substring(first.length() + 1, next > 0 ? next : host.length());
                final String named = second + Const.DOUBLE_COLONS_SPLIT + first;
                if (!second.isEmpty() && knownServices.contains(named)) {
                    return named;
                }
            }
            // The whole host in the caller's own group: a bare host as Kubernetes resolves it, and a short name that
            // holds a dot itself, checkout.api of shop::checkout.api. A qualified host names a service of the
            // caller's group this way only when one is called exactly that, never by its first label.
            final String group = KnownServices.groupOf(caller);
            if (group != null && knownServices.contains(group + Const.DOUBLE_COLONS_SPLIT + host)) {
                return group + Const.DOUBLE_COLONS_SPLIT + host;
            }
        }
        if (knownServices.contains(host)) {
            return host;
        }
        return qualified && knownServices.contains(first) ? first : null;
    }

    /**
     * The guess {@link #serviceNamedBy} does not make: the one grouped service, in any group, whose short name the
     * whole host is. Ranked last, since a node that has seen only one group's {@code cart} would name it for every
     * group's callers. A qualified host is never guessed by its first label: {@code cart.outlet} says its group, and
     * another group's {@code cart} is the wrong answer even when it is the only one known. The short name must equal
     * the whole host: {@code cart} is the one {@code x::cart}, and {@code checkout.api} the one
     * {@code shop::checkout.api}.
     *
     * @param host a host name
     * @return that service; null when no group, or more than one, has a service whose short name is the whole host
     */
    public String serviceByShortName(final String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        return knownServices.nameOf(host);
    }

    /**
     * @return the host up to its first dot, the whole host when it has none
     */
    private static String firstLabelOf(final String host) {
        final int dot = host.indexOf('.');
        return dot > 0 ? host.substring(0, dot) : host;
    }

    /**
     * @return the short name of a grouped service, else the name itself
     */
    private static String shortNameOf(final String service) {
        final String shortName = KnownServices.shortNameOf(service);
        return shortName != null ? shortName : service;
    }

    /**
     * @param address {@code host}, {@code host:port} or {@code [v6]:port}
     * @return the host part
     */
    public static String hostOf(final String address) {
        final String trimmed = address.trim();
        if (trimmed.startsWith("[")) {
            final int end = trimmed.indexOf(']');
            return end > 0 ? trimmed.substring(1, end) : trimmed;
        }
        final int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.indexOf(':') == colon
                && trimmed.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            return trimmed.substring(0, colon);
        }
        return trimmed;
    }
}
