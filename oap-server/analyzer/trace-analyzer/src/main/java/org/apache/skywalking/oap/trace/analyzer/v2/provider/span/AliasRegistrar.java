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

import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
public class AliasRegistrar {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final KnownServices knownServices;

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
     * @return whether a row was written; false for an empty address or service, or when the guard refused it
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
        final String named = serviceNamedBy(hostOf(address));
        if (named != null && !named.equals(service) && !named.equals(formatted)) {
            log.debug("Alias {} for {} skipped: the address names the service {}", address, formatted, named);
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
     * @param host a host name
     * @return the known service the host names, by the whole host or by its first DNS label; null when neither
     * has emitted spans
     */
    public String serviceNamedBy(final String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        if (knownServices.contains(host)) {
            return host;
        }
        final int dot = host.indexOf('.');
        if (dot > 0 && knownServices.contains(host.substring(0, dot))) {
            return host.substring(0, dot);
        }
        return null;
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
