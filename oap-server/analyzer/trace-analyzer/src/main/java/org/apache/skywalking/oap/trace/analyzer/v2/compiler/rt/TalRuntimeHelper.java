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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.meter.EntityTrafficGenerator;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;

/**
 * Every {@code h.*} call the generated rule class makes. One instance per execution, holding the context so the
 * generated source stays short.
 *
 * <p>Sibling implementation: {@code LalRuntimeHelper} in {@code log-analyzer} serves LAL the same way. The
 * conversion helpers are deliberately identical in behaviour; the span vocabulary below is TAL's own.
 */
@Slf4j
public class TalRuntimeHelper {
    private static final Gson GSON = new Gson();

    private final ExecutionContext ctx;
    private final FilterSpec spec;
    private IComponentLibraryCatalogService componentLibrary;
    private NetworkAddressAliasCache aliasCache;

    public TalRuntimeHelper(final ExecutionContext ctx, final FilterSpec spec) {
        this.ctx = ctx;
        this.spec = spec;
    }

    public ExecutionContext ctx() {
        return ctx;
    }

    public MeterSystem meterSystem() {
        return spec.getMeterSystem();
    }

    // ==================== Span vocabulary ====================

    /**
     * {@code attr(key, ...)} — the first present span attribute among the keys.
     *
     * @param keys the attribute names to try, current spelling first
     * @return the value as a string, empty when none of the keys is present
     */
    public String attr(final String... keys) {
        return firstPresent(ctx.spanAttributes(), keys);
    }

    /**
     * @param keys the attribute names to try
     * @return whether any of them is present on the span
     */
    public boolean hasAttr(final String... keys) {
        final Map<String, String> attributes = ctx.spanAttributes();
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
     * @param keys the attribute names to try
     * @return the value as a string, empty when none is present
     */
    public String resource(final String... keys) {
        return firstPresent(ctx.getResourceAttributes(), keys);
    }

    /**
     * {@code scopeAttr(key, ...)} — the first present instrumentation-scope attribute among the keys.
     *
     * @param keys the attribute names to try
     * @return the value as a string, empty when none is present
     */
    public String scopeAttr(final String... keys) {
        return firstPresent(ctx.scopeAttributes(), keys);
    }

    /**
     * {@code event(name)} — the first span event with that name.
     *
     * @param name the event name
     * @return the event, or null; the engine does not read it, the rule does
     */
    public Object event(final String name) {
        return ctx.getSourceTypeProvider() == null
            ? null : ctx.getSourceTypeProvider().event(ctx.getInput(), name);
    }

    /**
     * {@code eventAttr(name, key, ...)} — an attribute of the named event.
     *
     * @param name the event name
     * @param keys the attribute names to try
     * @return the value as a string, empty when the event or every key is absent
     */
    public String eventAttr(final String name, final String... keys) {
        if (ctx.getSourceTypeProvider() == null) {
            return "";
        }
        for (final String key : keys) {
            final String value = ctx.getSourceTypeProvider().eventAttribute(ctx.getInput(), name, key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * {@code component(name)} — the component id from {@code component-libraries.yml}, matched exactly and then
     * ignoring case, so {@code grpc} as {@code rpc.system} spells it finds {@code GRPC} and {@code postgresql}
     * finds {@code PostgreSQL}.
     *
     * @param name the component name a span attribute carried
     * @return the id, or 0, the Unknown component, when nothing matches
     */
    public int component(final String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        try {
            if (componentLibrary == null) {
                componentLibrary = spec.getModuleManager()
                                       .find(CoreModule.NAME)
                                       .provider()
                                       .getService(IComponentLibraryCatalogService.class);
            }
            return componentLibrary.getComponentIdIgnoreCase(name);
        } catch (final Exception e) {
            log.debug("Unknown component name {} in a TAL rule", name, e);
            return 0;
        }
    }

    /**
     * Layer names already warned about, so an unknown name in a rule costs one log line rather than one per span.
     * Bounded by the names rules spell, which is a handful; the cap only guards a rule that computes names.
     */
    private static final Set<String> REPORTED_UNKNOWN_LAYERS = ConcurrentHashMap.newKeySet();

    private static final int REPORTED_UNKNOWN_LAYERS_CAP = 1000;

    /**
     * @param name a layer name, from a literal or an expression
     * @return the layer; null when the name is empty or not registered, which {@link #layerResolved} turns into
     * skipping the metrics block. An empty name is a `layer: auto` rule whose extractor named no layer, whose output
     * is dropped rather than filed under a layer nobody chose. {@code Layer.nameOf} is not used because it answers
     * {@code UNDEFINED} for an unknown name, and {@code UNDEFINED} is a layer a rule names on purpose, for the
     * {@code User} end of a relation.
     */
    public Layer layer(final String name) {
        if (name == null || name.isEmpty()) {
            if (REPORTED_UNKNOWN_LAYERS.size() < REPORTED_UNKNOWN_LAYERS_CAP && REPORTED_UNKNOWN_LAYERS.add("")) {
                log.warn("A TAL rule built an entity with no layer; its metrics blocks are skipped. A `layer: auto` "
                    + "rule names the layer in its extractor");
            }
            return null;
        }
        try {
            return Layer.valueOf(name);
        } catch (final IllegalArgumentException e) {
            if (REPORTED_UNKNOWN_LAYERS.size() < REPORTED_UNKNOWN_LAYERS_CAP && REPORTED_UNKNOWN_LAYERS.add(name)) {
                log.warn("TAL rule named an unknown layer '{}'; the metrics blocks on it are skipped", name);
            }
            return null;
        }
    }

    /**
     * Metric names already warned about, as {@link #REPORTED_UNKNOWN_LAYERS} is for layers.
     */
    private static final Set<String> REPORTED_FAILED_METRICS = ConcurrentHashMap.newKeySet();

    /**
     * A {@code metrics {}} block threw, typically on a value that does not parse as a number; the generated code
     * has skipped that block only. The first failure of each metric is a warning, later ones are debug.
     *
     * @param metricName the metric the block feeds
     * @param e          what it threw
     */
    public void metricsBlockFailed(final String metricName, final RuntimeException e) {
        if (REPORTED_FAILED_METRICS.size() < REPORTED_UNKNOWN_LAYERS_CAP && REPORTED_FAILED_METRICS.add(metricName)) {
            log.warn("TAL metrics block {} failed on a span and is skipped for it; later failures are logged at debug",
                metricName, e);
        } else if (log.isDebugEnabled()) {
            log.debug("TAL metrics block {} failed on a span", metricName, e);
        }
    }

    /**
     * @param entity the entity a metrics block is about to feed
     * @return whether every layer the entity was built with resolved, see {@link #layer}; the generated block
     * runs only then
     */
    public boolean layerResolved(final MeterEntity entity) {
        if (entity.getLayer() == null) {
            return false;
        }
        final boolean relation = entity.getScopeType() == ScopeType.SERVICE_RELATION
            || entity.getScopeType() == ScopeType.SERVICE_INSTANCE_RELATION;
        return !relation || entity.getDestLayer() != null;
    }

    // ==================== Network address aliases ====================

    /**
     * {@code alias <address>} — register the address this span was dialed at as an alias of the span's service,
     * the row the agent path writes from the {@code sw8} header's peer address. OTLP propagates no such header,
     * so the address is whatever the rule read, normally {@code server.address:server.port} of a SERVER span,
     * which the HTTP semantic conventions fill from the {@code Host} or {@code :authority} the caller sent.
     *
     * <p>The represented service's id carries the {@code normal} flag of {@code meta.layer} as
     * {@code service(meta.service, meta.layer)} builds it, so a {@link #resolveAlias} hit names the same entity
     * the SERVER span's own metrics went to. The row reaches {@link NetworkAddressAliasCache} through storage and
     * the core's cache timer, not directly.
     *
     * <p>An address whose host names another service is not registered. A proxy that keeps the {@code Host}
     * header hands its own address to the service behind it, which would otherwise claim it and pull every edge
     * to the proxy onto itself; {@link AliasRegistrar} applies that guard.
     *
     * @param address the address as the caller spelled it; an empty one registers nothing
     */
    public void alias(final String address) {
        final SpanMetadata meta = ctx.getMeta();
        final Layer serviceLayer = StringUtil.isEmpty(meta.getLayer()) ? null : layer(meta.getLayer());
        spec.getAliasRegistrar().register(address, meta.getService(), MeterEntity.isNormal(serviceLayer),
            meta.getServiceInstance(), meta.getTimestamp());
    }

    /**
     * The authority of a URL, host and port as written, so a client that names its target only as a URL still
     * gets a peer: the Python requests instrumentation sets {@code http.url} and no server.address.
     *
     * @param url an absolute URL, or anything else
     * @return {@code host} or {@code host:port}, without scheme, user info, path, query or fragment; empty when
     * the value has no authority, which a value without a {@code scheme://} prefix, a bare path, does not
     */
    public String urlHost(final String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        final int scheme = url.indexOf("://");
        if (scheme <= 0) {
            return "";
        }
        final int start = scheme + 3;
        int end = url.length();
        for (final char stop : new char[] {'/', '?', '#'}) {
            final int at = url.indexOf(stop, start);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String authority = url.substring(start, end);
        final int userInfo = authority.lastIndexOf('@');
        if (userInfo >= 0) {
            authority = authority.substring(userInfo + 1);
        }
        return authority.trim();
    }

    /**
     * {@code resolveAlias(address)} — the service some SERVER span registered the address for through
     * {@link #alias}, or the address unchanged when none did. Read from the core's {@link NetworkAddressAliasCache},
     * which {@code CacheUpdateTimer} reloads from storage every 10 seconds, so the first edges to a freshly seen
     * address stay address-named until its row lands; {@code ServiceTopologyBuilder} merges those at query time
     * through the same cache.
     *
     * <p>A host that names a service this OAP has seen spans from is that service, before the cache is asked:
     * {@code product-catalog:3550} is {@code product-catalog} even when its SERVER spans report some other
     * address, and {@code frontend-proxy:8080} is the proxy even when the service behind it registered that
     * address. The first DNS label counts too, so a cluster FQDN resolves to the service it starts with. A grouped
     * service is named as Kubernetes DNS resolves a Service: {@code cart.outlet} is {@code outlet::cart}, and a
     * bare {@code cart} is the caller's own group's (see {@link AliasRegistrar#serviceNamedBy}). Another group's
     * {@code cart} is only a guess, asked last.
     *
     * <p>When none names it, the span is handed to {@link SpanPairing}, whose {@link SpanPairing#clientSeen}
     * answers with a service an earlier pair named; otherwise, short of another group's service of the same short
     * name, the edge stays address-named this once, and the alias row the pair writes names the calls that follow.
     *
     * @param address the peer as the CLIENT span spelled it
     * @return the represented service's name, or the address
     */
    public String resolveAlias(final String address) {
        if (address == null || address.isEmpty()) {
            return address == null ? "" : address;
        }
        final AliasRegistrar registrar = spec.getAliasRegistrar();
        final String host = AliasRegistrar.hostOf(address);
        final String named = registrar.serviceNamedBy(host, ctx.getMeta().getService());
        if (named != null) {
            return named;
        }
        final String cached = cachedAlias(address);
        if (cached != null) {
            return cached;
        }
        // Pairing before the guess: clientSeen is also what reports this call's fact, so the pair can learn the
        // address and write its row for the calls that follow.
        final String learned = spec.getSpanPairing().clientSeen(ctx.getMeta(), address);
        if (learned != null) {
            return learned;
        }
        final String guessed = registrar.serviceByShortName(host);
        return guessed != null ? guessed : address;
    }

    /**
     * {@code isGateway(address)} — whether the address is listed in the agent analyzer's {@code gateways.yml}, so a
     * rule can draw the gateway between a caller and the service behind it as the agent path does. Always false
     * when the agent analyzer is not loaded.
     *
     * @param address {@code host} or {@code host:port}; a bare host is matched at port 80
     * @return whether the address is an uninstrumented gateway
     */
    public boolean isGateway(final String address) {
        return spec.getAliasRegistrar().isGateway(address);
    }

    private String cachedAlias(final String address) {
        try {
            if (aliasCache == null) {
                aliasCache = spec.getModuleManager()
                                 .find(CoreModule.NAME)
                                 .provider()
                                 .getService(NetworkAddressAliasCache.class);
            }
            final NetworkAddressAlias alias = aliasCache.get(address);
            return alias == null ? null : IDManager.ServiceID.analysisId(alias.getRepresentServiceId()).getName();
        } catch (final Exception e) {
            log.debug("Network address alias lookup failed for {} in a TAL rule", address, e);
            return null;
        }
    }

    // ==================== Metrics ====================

    /**
     * Register the entity so it appears in the service lists and the topology, in the minute its metric lands in.
     * Called before every metric is accepted; the rows are written once per distinct entity per execution,
     * however many metrics blocks share the scope.
     *
     * <p>A service or endpoint entity carries its layer's name as {@code attr0}, as {@code ServiceDecorator} and
     * {@code EndpointDecorator} give the agent metrics, so {@code top_n(..., attr0='...')} filters these too.
     *
     * @param entity     the entity a metric is about to be accepted for
     * @param timeMillis the time the metric is bucketed by
     */
    public void traffic(final MeterEntity entity, final long timeMillis) {
        if ((entity.getScopeType() == ScopeType.SERVICE || entity.getScopeType() == ScopeType.ENDPOINT)
            && entity.getLayer() != null) {
            entity.setAttr0(entity.getLayer().name());
        }
        if (ctx.markTrafficked(entity)) {
            EntityTrafficGenerator.generate(entity, timeMillis);
        } else if (entity.getScopeType() == ScopeType.SERVICE_RELATION && entity.getDetectPoint() != null) {
            // generate() also names the side that owns a service relation's metric, see
            // EntityTrafficGenerator.serviceRelationTraffic; a second entity for an already registered relation,
            // built again by an inline serviceRelation(...), still needs that owner, or its metric files under
            // an empty service.
            entity.setServiceName(entity.getDetectPoint() == DetectPoint.SERVER
                ? entity.getDestServiceName() : entity.getSourceServiceName());
        }
    }

    /**
     * Hand a metric value to the meter system in the minute bucket the span's timestamp falls in.
     *
     * @param value the value already accepted for its entity
     * @param time  milliseconds; the span's end unless the block named another
     */
    public void send(final AcceptableValue<?> value, final long time) {
        value.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        spec.getMeterSystem().doStreamingCalculation(value);
    }

    /**
     * Place one observation in the bucket it falls into: the first bound not below it, or the last bucket when
     * it is above every bound. The generated code always passes {@code Long.MAX_VALUE} as the last bound, so that
     * last bucket is the overflow bucket.
     *
     * @param bounds the ascending bucket bounds, in the unit of the observation
     * @param value  the observation
     * @return the bucketed value, counting 1 in its bucket
     */
    public BucketedValues bucketed(final long[] bounds, final long value) {
        final long[] counts = new long[bounds.length];
        int index = bounds.length - 1;
        for (int i = 0; i < bounds.length; i++) {
            if (value <= bounds[i]) {
                index = i;
                break;
            }
        }
        counts[index] = 1L;
        return new BucketedValues(bounds, counts);
    }

    /**
     * Build the one-entry table a labeled function accepts. The stored table separates entries with {@code |}
     * and a label set's labels with {@code ,}, so those two characters in a value are replaced by {@code _};
     * kept as they are, they would split the value when the table is read back.
     *
     * @param labels alternating label name and value
     * @param value  the observation
     * @return the table keyed by the label set
     */
    public DataTable labeled(final String[] labels, final long value) {
        final DataLabel label = new DataLabel();
        for (int i = 0; i + 1 < labels.length; i += 2) {
            label.put(labels[i], labels[i + 1] == null ? "" : labels[i + 1].replace('|', '_').replace(',', '_'));
        }
        final DataTable table = new DataTable();
        table.put(label, value);
        return table;
    }

    /**
     * attr() with 1 key; spelled out because Javassist does not adapt varargs.
     */
    public String attr(final String k0) {
        return firstPresent(ctx.spanAttributes(), new String[] {k0});
    }

    /**
     * attr() with 2 keys; spelled out because Javassist does not adapt varargs.
     */
    public String attr(final String k0, final String k1) {
        return firstPresent(ctx.spanAttributes(), new String[] {k0, k1});
    }

    /**
     * attr() with 3 keys; spelled out because Javassist does not adapt varargs.
     */
    public String attr(final String k0, final String k1, final String k2) {
        return firstPresent(ctx.spanAttributes(), new String[] {k0, k1, k2});
    }

    /**
     * attr() with 4 keys; spelled out because Javassist does not adapt varargs.
     */
    public String attr(final String k0, final String k1, final String k2, final String k3) {
        return firstPresent(ctx.spanAttributes(), new String[] {k0, k1, k2, k3});
    }

    /**
     * resource() with 1 key; spelled out because Javassist does not adapt varargs.
     */
    public String resource(final String k0) {
        return firstPresent(ctx.getResourceAttributes(), new String[] {k0});
    }

    /**
     * resource() with 2 keys; spelled out because Javassist does not adapt varargs.
     */
    public String resource(final String k0, final String k1) {
        return firstPresent(ctx.getResourceAttributes(), new String[] {k0, k1});
    }

    /**
     * resource() with 3 keys; spelled out because Javassist does not adapt varargs.
     */
    public String resource(final String k0, final String k1, final String k2) {
        return firstPresent(ctx.getResourceAttributes(), new String[] {k0, k1, k2});
    }

    /**
     * resource() with 4 keys; spelled out because Javassist does not adapt varargs.
     */
    public String resource(final String k0, final String k1, final String k2, final String k3) {
        return firstPresent(ctx.getResourceAttributes(), new String[] {k0, k1, k2, k3});
    }

    /**
     * scopeAttr() with 1 key; spelled out because Javassist does not adapt varargs.
     */
    public String scopeAttr(final String k0) {
        return firstPresent(ctx.scopeAttributes(), new String[] {k0});
    }

    /**
     * scopeAttr() with 2 keys; spelled out because Javassist does not adapt varargs.
     */
    public String scopeAttr(final String k0, final String k1) {
        return firstPresent(ctx.scopeAttributes(), new String[] {k0, k1});
    }

    /**
     * scopeAttr() with 3 keys; spelled out because Javassist does not adapt varargs.
     */
    public String scopeAttr(final String k0, final String k1, final String k2) {
        return firstPresent(ctx.scopeAttributes(), new String[] {k0, k1, k2});
    }

    /**
     * scopeAttr() with 4 keys; spelled out because Javassist does not adapt varargs.
     */
    public String scopeAttr(final String k0, final String k1, final String k2, final String k3) {
        return firstPresent(ctx.scopeAttributes(), new String[] {k0, k1, k2, k3});
    }

    /**
     * hasAttr() with 1 key; spelled out because Javassist does not adapt varargs.
     */
    public boolean hasAttr(final String k0) {
        return hasAttr(new String[] {k0});
    }

    /**
     * hasAttr() with 2 keys; spelled out because Javassist does not adapt varargs.
     */
    public boolean hasAttr(final String k0, final String k1) {
        return hasAttr(new String[] {k0, k1});
    }

    /**
     * hasAttr() with 3 keys; spelled out because Javassist does not adapt varargs.
     */
    public boolean hasAttr(final String k0, final String k1, final String k2) {
        return hasAttr(new String[] {k0, k1, k2});
    }

    /**
     * hasAttr() with 4 keys; spelled out because Javassist does not adapt varargs.
     */
    public boolean hasAttr(final String k0, final String k1, final String k2, final String k3) {
        return hasAttr(new String[] {k0, k1, k2, k3});
    }

    /**
     * eventAttr() with 1 attribute key; spelled out for Javassist.
     */
    public String eventAttr(final String name, final String k0) {
        return eventAttr(name, new String[] {k0});
    }

    /**
     * eventAttr() with 2 attribute keys; spelled out for Javassist.
     */
    public String eventAttr(final String name, final String k0, final String k1) {
        return eventAttr(name, new String[] {k0, k1});
    }

    /**
     * eventAttr() with 3 attribute keys; spelled out for Javassist.
     */
    public String eventAttr(final String name, final String k0, final String k1, final String k2) {
        return eventAttr(name, new String[] {k0, k1, k2});
    }
    // ==================== Comparisons ====================

    /**
     * What an {@code as T} answers in a comparison when the value cannot be converted; a comparison with it is false.
     */
    private static final Object CAST_FAILED = new Object();

    /**
     * {@code value as Integer} in a comparison.
     *
     * @param value the value to convert
     * @return the Integer, or a failure marker for a value that is absent or not a whole number of that range
     */
    public Object asInteger(final Object value) {
        final Object number = asNumber(value);
        if (number instanceof Number) {
            final double d = ((Number) number).doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return ((Number) number).intValue();
            }
        }
        return CAST_FAILED;
    }

    /**
     * {@code value as Long} in a comparison.
     *
     * @param value the value to convert
     * @return the Long, or a failure marker for a value that is absent or not a whole number
     */
    public Object asLong(final Object value) {
        final Object number = asNumber(value);
        if (number instanceof Long || number instanceof Integer) {
            return ((Number) number).longValue();
        }
        if (number instanceof Double && ((Double) number) == Math.rint((Double) number)) {
            return ((Double) number).longValue();
        }
        return CAST_FAILED;
    }

    /**
     * {@code value as Double} in a comparison.
     *
     * @param value the value to convert
     * @return the Double, or a failure marker for a value that is absent or not a number
     */
    public Object asDouble(final Object value) {
        final Object number = asNumber(value);
        return number instanceof Number ? (Object) ((Number) number).doubleValue() : CAST_FAILED;
    }

    /**
     * {@code value as Float} in a comparison.
     *
     * @param value the value to convert
     * @return the Float, or a failure marker for a value that is absent or not a number
     */
    public Object asFloat(final Object value) {
        final Object number = asNumber(value);
        return number instanceof Number ? (Object) ((Number) number).floatValue() : CAST_FAILED;
    }

    /**
     * {@code value as Boolean} in a comparison.
     *
     * @param value the value to convert
     * @return the Boolean, or a failure marker for anything but a Boolean or the text true or false
     */
    public Object asBoolean(final Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.valueOf(text);
            }
        }
        return CAST_FAILED;
    }

    /**
     * @return a Long or a Double for a number or a text that parses as one, else null
     */
    private static Object asNumber(final Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (final NumberFormatException e) {
                try {
                    return Double.parseDouble(text);
                } catch (final NumberFormatException e2) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * A comparison of two numbers with Java's promotion: as doubles when either is a floating-point number, as
     * longs otherwise.
     *
     * @param left  the left operand
     * @param right the right operand
     * @param op    the operator, the ordinal of {@code CompareOp}: ==, !=, &gt;, &lt;, &gt;=, &lt;=
     * @return the comparison; false when either side is absent or a failed {@code as T}
     */
    public boolean compareNumbers(final Object left, final Object right, final int op) {
        if (!(left instanceof Number) || !(right instanceof Number)) {
            return false;
        }
        final int sign;
        if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
            final double l = ((Number) left).doubleValue();
            final double r = ((Number) right).doubleValue();
            if (Double.isNaN(l) || Double.isNaN(r)) {
                return op == 1;
            }
            sign = l < r ? -1 : l > r ? 1 : 0;
        } else {
            sign = Long.compare(((Number) left).longValue(), ((Number) right).longValue());
        }
        switch (op) {
            case 0:
                return sign == 0;
            case 1:
                return sign != 0;
            case 2:
                return sign > 0;
            case 3:
                return sign < 0;
            case 4:
                return sign >= 0;
            case 5:
            default:
                return sign <= 0;
        }
    }

    /**
     * {@code ==} or {@code !=} of two values of one kind.
     *
     * @param left  the left operand
     * @param right the right operand
     * @param equal true for {@code ==}
     * @return whether they are equal, or differ; false when either side is a failed {@code as T}
     */
    public boolean sameValue(final Object left, final Object right, final boolean equal) {
        if (left == CAST_FAILED || right == CAST_FAILED) {
            return false;
        }
        return Objects.equals(left, right) == equal;
    }

    // ==================== Conversions ====================

    /**
     * Javassist does not autobox, so the primitive results of a chain or a built-in reach the conversions as
     * primitives and need their own overloads.
     */
    public int toInt(final int value) {
        return value;
    }

    public int toInt(final long value) {
        return (int) value;
    }

    public long toLong(final long value) {
        return value;
    }

    public long toLong(final int value) {
        return value;
    }

    public double toDouble(final double value) {
        return value;
    }

    public float toFloat(final float value) {
        return value;
    }

    public boolean toBool(final boolean value) {
        return value;
    }

    public String toStr(final int value) {
        return String.valueOf(value);
    }

    public String toStr(final long value) {
        return String.valueOf(value);
    }

    public String toStr(final boolean value) {
        return String.valueOf(value);
    }

    public String toStr(final Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    public long toLong(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            return Long.parseLong((String) obj);
        }
        return 0L;
    }

    public int toInt(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return 0;
    }

    public double toDouble(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            return Double.parseDouble((String) obj);
        }
        return 0.0d;
    }

    public float toFloat(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).floatValue();
        }
        if (obj instanceof String) {
            return Float.parseFloat((String) obj);
        }
        return 0.0f;
    }

    public boolean toBool(final Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return obj != null;
    }

    /**
     * Truthiness for an {@code if} over a bare expression: null is false, a Boolean speaks for itself, a String
     * parses, anything else present is true.
     *
     * @param obj the value the condition read
     * @return whether the condition holds
     */
    public boolean isTrue(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return true;
    }

    public boolean isTrue(final boolean value) {
        return value;
    }

    /**
     * Non-emptiness for an {@code if} over a bare expression, which is how {@code if (!peer)} reads. A boxed
     * boolean is its truth, so {@code if (meta.root)} reads as a condition rather than as the non-empty string
     * {@code "false"}; LAL's helper has no such case, since a log field is text.
     *
     * @param obj the value the condition read
     * @return whether it is present and not the empty string, or true for a {@code Boolean}
     */
    public boolean isNotEmpty(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return !((String) obj).isEmpty();
        }
        return !obj.toString().isEmpty();
    }

    public boolean isNotEmpty(final boolean value) {
        return value;
    }

    /**
     * Null-safe {@code ?.toString()}.
     *
     * @param obj the value
     * @return its string form, or null
     */
    public String toString(final Object obj) {
        return obj == null ? null : obj.toString();
    }

    public String trim(final Object obj) {
        return obj == null ? null : obj.toString().trim();
    }

    public JsonObject toJsonObject(final String s) {
        if (s == null || s.isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (final RuntimeException e) {
            log.debug("TAL toJson() could not parse the value as a JSON object", e);
            return new JsonObject();
        }
    }

    public JsonObject toJsonObject(final Object obj) {
        if (obj == null) {
            return new JsonObject();
        }
        if (obj instanceof JsonObject) {
            return (JsonObject) obj;
        }
        if (obj instanceof String) {
            return toJsonObject((String) obj);
        }
        return GSON.toJsonTree(obj).getAsJsonObject();
    }

    public JsonArray toJsonArray(final String s) {
        if (s == null || s.isEmpty()) {
            return new JsonArray();
        }
        try {
            return JsonParser.parseString(s).getAsJsonArray();
        } catch (final RuntimeException e) {
            log.debug("TAL toJsonArray() could not parse the value as a JSON array", e);
            return new JsonArray();
        }
    }

    public JsonArray toJsonArray(final Object obj) {
        if (obj == null) {
            return new JsonArray();
        }
        if (obj instanceof JsonArray) {
            return (JsonArray) obj;
        }
        if (obj instanceof String) {
            return toJsonArray((String) obj);
        }
        return GSON.toJsonTree(obj).getAsJsonArray();
    }

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
