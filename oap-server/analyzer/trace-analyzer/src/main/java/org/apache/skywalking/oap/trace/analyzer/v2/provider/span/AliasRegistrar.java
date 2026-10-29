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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;

/**
 * Writes the network address alias rows {@link SpanPairing} builds. As on the agent path, an address listed in
 * {@code gateways.yml} is never aliased, so an uninstrumented gateway keeps its own node between its callers and the
 * services behind it, and otherwise the last write wins.
 */
public class AliasRegistrar {
    /**
     * The port {@code gateways.yml} assumes for an instance that names none.
     */
    private static final String GATEWAY_DEFAULT_PORT = ":80";

    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    /**
     * Whether an address is listed in {@code gateways.yml}; absent when the agent analyzer is not loaded.
     */
    @Setter
    private Predicate<String> gateways;

    public AliasRegistrar(final SourceReceiver sourceReceiver, final NamingControl namingControl) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
    }

    /**
     * Register the address as an alias of the service. The row names no instance: one caller and peer reach every
     * instance of the callee.
     *
     * @param address         the alias address
     * @param service         the service it represents, unformatted
     * @param normal          the service id's {@code normal} flag, as its own metrics carry it
     * @param writtenAtMillis when the row is pushed, which picks its retention time bucket; the update bucket the
     *                        alias cache reloads by is the minute OAP receives it, set by the dispatcher
     * @return whether a row was pushed to the alias stream, false for an empty address or service; whether storage
     * writes it is {@code NetworkAddressAlias#combine}'s decision
     */
    public boolean register(final String address, final String service, final boolean normal,
                            final long writtenAtMillis) {
        if (address == null || address.isEmpty() || sourceReceiver == null) {
            return false;
        }
        final String formatted = namingControl.formatServiceName(service);
        if (formatted == null || formatted.isEmpty()) {
            return false;
        }
        final NetworkAddressAliasSetup setup = new NetworkAddressAliasSetup();
        setup.setAddress(address);
        setup.setRepresentService(formatted);
        setup.setRepresentServiceNormal(normal);
        setup.setRepresentServiceInstance("");
        setup.setTimeBucket(TimeBucket.getMinuteTimeBucket(writtenAtMillis));
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
     * @param address {@code host}, {@code host:port} or {@code [v6]:port}
     * @return the host part
     */
    static String hostOf(final String address) {
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
