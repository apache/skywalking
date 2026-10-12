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
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
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
     * @return the layer; {@link Layer#UNDEFINED} for an empty name; null when the name is not registered, which
     * {@link #layerResolved} turns into skipping the metrics block. {@code Layer.nameOf} is not used because it
     * answers {@code UNDEFINED} for an unknown name as well, and {@code UNDEFINED} is a layer a rule may name,
     * for the {@code User} end of a relation.
     */
    public Layer layer(final String name) {
        if (name == null || name.isEmpty()) {
            return Layer.UNDEFINED;
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
        spec.getAliasRegistrar().register(address, meta.getService(), MeterEntity.isNormal(layer(meta.getLayer())),
            meta.getServiceInstance(), meta.getTimestamp());
    }

    /**
     * The authority of a URL, host and port as written, so a client that names its target only as a URL still
     * gets a peer: the Python requests instrumentation sets {@code http.url} and no server.address.
     *
     * @param url an absolute URL, or anything else
     * @return {@code host} or {@code host:port}, without scheme, user info, path, query or fragment; empty when
     * the value has no authority
     */
    public String urlHost(final String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        final int scheme = url.indexOf("://");
        final int start = scheme < 0 ? 0 : scheme + 3;
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
     * address. The first DNS label counts too, so a cluster FQDN resolves to the service it starts with.
     *
     * <p>When neither names it, the span is handed to {@link SpanPairing}: if the SERVER span this call produced
     * has already been analyzed here, its service is the answer and the alias row is learned from the pair;
     * otherwise the call waits for that span, and the edge stays address-named this once.
     *
     * @param address the peer as the CLIENT span spelled it
     * @return the represented service's name, or the address
     */
    public String resolveAlias(final String address) {
        if (address == null || address.isEmpty()) {
            return address == null ? "" : address;
        }
        final String named = spec.getAliasRegistrar().serviceNamedBy(AliasRegistrar.hostOf(address));
        if (named != null) {
            return named;
        }
        final String cached = cachedAlias(address);
        if (cached != null) {
            return cached;
        }
        final String learned = spec.getSpanPairing().clientSeen(ctx.getMeta(), address);
        return learned != null ? learned : address;
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
     * Register the entity so it appears in the service lists and the topology. Called once per distinct entity
     * per execution, however many metrics blocks share the scope.
     *
     * @param entity the entity a metric was just accepted for
     */
    public void traffic(final MeterEntity entity) {
        if (ctx.markTrafficked(entity)) {
            EntityTrafficGenerator.generate(entity);
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
     * it is above every bound, which makes the last bound a finite overflow sentinel.
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
     * Build the one-entry table a labeled function accepts.
     *
     * @param labels alternating label name and value
     * @param value  the observation
     * @return the table keyed by the label set
     */
    public DataTable labeled(final String[] labels, final long value) {
        final DataLabel label = new DataLabel();
        for (int i = 0; i + 1 < labels.length; i += 2) {
            label.put(labels[i], labels[i + 1] == null ? "" : labels[i + 1]);
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
