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

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.ServerClusterConfig;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalConfigFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalGeneralFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OTLPValues;

/**
 * The OTLP span as a TAL input, under the alias {@code otlp}. It is the default a rule gets when it declares no
 * {@code inputType}. Its functions, the methods below that take the run first, read the OTLP span's own data, its
 * attributes, events, resource and scope, and look up the network aliases and server clusters a peer's name goes
 * through; the readers they use are the methods that take the span itself. An input that builds through span metadata
 * as OTLP does extends this provider and reads its own span in the readers.
 */
@Slf4j
public class OtlpTALSourceTypeProvider implements TALSourceTypeProvider {
    @Override
    public String alias() {
        return "otlp";
    }

    @Override
    public Class<?> inputType() {
        return Span.class;
    }

    @Override
    public Set<TALConstruct> constructs() {
        return TALConstruct.SPAN_METADATA;
    }

    /**
     * @return the logic and configuration functions every shipped input shares
     */
    @Override
    public List<TALFunctionProvider> helperFunctions() {
        return List.of(new TalGeneralFunctions(), new TalConfigFunctions());
    }


    /**
     * The span's own attributes. The run indexes them once per span and serves every {@code attr()} and
     * {@code hasAttr()} of that span from the index.
     *
     * @param input the span, an instance of {@link #inputType()}
     * @return the attributes with their values rendered as strings; an absent key is absent from the map
     */
    public Map<String, String> spanAttributes(final Object input) {
        return input instanceof Span
            ? OTLPValues.toStringMap(((Span) input).getAttributesList()) : Collections.emptyMap();
    }

    /**
     * @param input the span, an instance of {@link #inputType()}
     * @param name  the event name to look for
     * @return the first event with that name, or null; the engine passes it to the rule without reading it
     */
    private static Object findEvent(final Object input, final String name) {
        if (!(input instanceof Span)) {
            return null;
        }
        for (final Span.Event event : ((Span) input).getEventsList()) {
            if (event.getName().equals(name)) {
                return event;
            }
        }
        return null;
    }

    /**
     * @param input     the span, an instance of {@link #inputType()}
     * @param eventName the event to look in
     * @param key       the attribute to read
     * @return the attribute's value as a string, empty when the event or the attribute is absent
     */
    private static String eventAttribute(final Object input, final String eventName, final String key) {
        final Object event = findEvent(input, eventName);
        if (!(event instanceof Span.Event)) {
            return "";
        }
        // One scan per key rather than a map per key: eventAttr() probes its keys one at a time. The first
        // occurrence of a duplicated key wins, as in OTLPValues.toStringMap.
        for (final KeyValue attribute : ((Span.Event) event).getAttributesList()) {
            if (key.equals(attribute.getKey())) {
                return OTLPValues.render(attribute.getValue());
            }
        }
        return "";
    }

    /**
     * {@code attr(key, ...)} — the first present span attribute among the keys.
     *
     * @param h    the run
     * @param keys the attribute names to try, current spelling first
     * @return the value as a string, empty when none of the keys is present
     */
    public String attr(final TalRuntimeHelper h, final String... keys) {
        return firstPresent(h.ctx().spanAttributes(), keys);
    }

    /**
     * @param h    the run
     * @param keys the attribute names to try
     * @return whether any of them is present on the span
     */
    public boolean hasAttr(final TalRuntimeHelper h, final String... keys) {
        final Map<String, String> attributes = h.ctx().spanAttributes();
        for (final String key : keys) {
            if (attributes.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code resource(key, ...)} — the first present resource attribute among the keys.
     *
     * @param h    the run
     * @param keys the attribute names to try
     * @return the value as a string, empty when none is present
     */
    public String resource(final TalRuntimeHelper h, final String... keys) {
        return firstPresent(h.ctx().getResourceAttributes(), keys);
    }

    /**
     * {@code scopeAttr(key, ...)} — the first present instrumentation-scope attribute among the keys.
     *
     * @param h    the run
     * @param keys the attribute names to try
     * @return the value as a string, empty when none is present
     */
    public String scopeAttr(final TalRuntimeHelper h, final String... keys) {
        return firstPresent(h.ctx().scopeAttributes(), keys);
    }

    /**
     * {@code event(name)} — the first span event with that name.
     *
     * @param h    the run
     * @param name the event name
     * @return the event, or null; the engine does not read it, the rule does
     */
    public Object event(final TalRuntimeHelper h, final String name) {
        return findEvent(h.ctx().getInput(), name);
    }

    /**
     * {@code eventAttr(name, key, ...)} — an attribute of the named event.
     *
     * @param h    the run
     * @param name the event name
     * @param keys the attribute names to try
     * @return the value as a string, empty when the event or every key is absent
     */
    public String eventAttr(final TalRuntimeHelper h, final String name, final String... keys) {
        for (final String key : keys) {
            final String value = eventAttribute(h.ctx().getInput(), name, key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * {@code callerScopedAddress(service, peer)} — the address an alias is filed under: the caller and the peer it
     * dialed, both as the naming rules format a service name, which is how the service id and the address node name
     * them, so the topology fold rebuilds the same address from an edge.
     *
     * @param h       the run
     * @param service the caller
     * @param peer    the peer it dialed
     * @return {@code service->peer}
     */
    public String callerScopedAddress(final TalRuntimeHelper h, final String service, final String peer) {
        final NamingControl naming = h.spec().getNamingControl();
        return IDManager.NetworkAddressAliasDefine.buildCallerScopedAddress(
            naming.formatServiceName(service == null ? "" : service), naming.formatServiceName(peer == null ? "" : peer));
    }

    /**
     * {@code resolveNetworkAlias(address)} — the service an alias address names, as the core's alias cache holds it.
     *
     * @param h       the run
     * @param address the alias address, as {@code callerScopedAddress} composes it
     * @return the represented service's name; otherwise the peer part of a caller-scoped address, so an unlearned
     * peer keeps its own name, or the address unchanged
     */
    public String resolveNetworkAlias(final TalRuntimeHelper h, final String address) {
        if (address == null || address.isEmpty()) {
            return address == null ? "" : address;
        }
        final NetworkAddressAlias stored = storedAlias(h, address);
        if (stored != null) {
            return IDManager.ServiceID.analysisId(stored.getRepresentServiceId()).getName();
        }
        // The peer is whatever follows this span's own caller, since a peer named by peer.service may hold `->`
        // itself; an address composed for another caller is cut at its first separator.
        final String ownPrefix = callerScopedAddress(h, h.ctx().getMeta().getService(), "");
        if (address.startsWith(ownPrefix)) {
            return address.substring(ownPrefix.length());
        }
        final int separator = address.indexOf(IDManager.NetworkAddressAliasDefine.CALLER_SCOPE_SEPARATOR);
        return separator < 0 ? address
            : address.substring(separator + IDManager.NetworkAddressAliasDefine.CALLER_SCOPE_SEPARATOR.length());
    }

    /**
     * {@code findServerClusterName(address)} — the cluster the core's {@code server-clusters.yml} lists the address
     * under, so a rule names a broker, a database or a cache reached at several addresses as one service. The rule
     * decides which address it asks for; a network alias is not consulted.
     *
     * @param h       the run
     * @param address one address, or a list joined by {@code ,} or {@code ;}, as the span reports it
     * @return the cluster name, or an empty string when the address is not listed, as {@code attr()} returns for an
     * absent attribute
     */
    public String findServerClusterName(final TalRuntimeHelper h, final String address) {
        return findServerClusterName(h, address, null);
    }

    /**
     * {@code findServerClusterName(address, kind)} — the same among the clusters whose {@code kinds} list the kind, a
     * name the user chose such as {@code postgresql} or {@code queue}, and those that list no kind.
     *
     * @param h       the run
     * @param address one address, or a list joined by {@code ,} or {@code ;}, as the span reports it
     * @param kind    the kind to match, null for every cluster
     * @return the cluster name, or an empty string when the address is not listed
     */
    public String findServerClusterName(final TalRuntimeHelper h, final String address, final String kind) {
        try {
            final String cluster = h.spec().service(CoreModule.NAME, ServerClusterConfig.class)
                                    .findClusterName(address, kind);
            return cluster == null ? "" : cluster;
        } catch (final Exception e) {
            log.debug("No server cluster lookup for {} in a TAL rule", address, e);
            return "";
        }
    }

    private static NetworkAddressAlias storedAlias(final TalRuntimeHelper h, final String address) {
        try {
            return h.spec().service(CoreModule.NAME, NetworkAddressAliasCache.class).get(address);
        } catch (final Exception e) {
            log.debug("Network address alias lookup failed for {} in a TAL rule", address, e);
            return null;
        }
    }

    /**
     * @return the value of the first key the source holds, empty when it holds none
     */
    private static String firstPresent(final Map<String, String> source, final String... keys) {
        for (final String key : keys) {
            final String value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
