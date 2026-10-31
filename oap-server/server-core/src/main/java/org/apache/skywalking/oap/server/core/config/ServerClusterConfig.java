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

package org.apache.skywalking.oap.server.core.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The clusters {@code server-clusters.yml} lists: an uninstrumented server, a message broker, a database or a cache,
 * that clients reach at more than one address, named as one cluster. The trace analyses ask
 * {@link #findClusterName(String, String)} where they name such a server, for an address no network alias maps. Empty by
 * default, so nothing is renamed until a cluster is listed; the dynamic configuration item
 * {@code core.default.serverClusters} replaces the file's content.
 */
@Slf4j
public class ServerClusterConfig extends ConfigChangeWatcher implements Service {
    public static final String CONFIG_FILE_NAME = "server-clusters.yml";
    private static final Pattern LIST_SEPARATORS = Pattern.compile("[,;]");

    private final int maxNameLength;
    /**
     * The file's content, validated at boot; a dynamic DELETE falls back to it.
     */
    private final Clusters fileClusters;
    private volatile Clusters clusters;
    private volatile String rawConfig;

    /**
     * @param provider      the core module provider that owns the watcher
     * @param maxNameLength the longest service name the core keeps, {@code core.default.serviceNameMaxLength}; a longer
     *                      cluster name would be cut and could collide with another
     */
    public ServerClusterConfig(final ModuleProvider provider, final int maxNameLength) {
        super(CoreModule.NAME, provider, "serverClusters");
        this.maxNameLength = maxNameLength;
        this.fileClusters = loadFile();
        this.clusters = fileClusters;
    }

    /**
     * @param address the address a span reports for a broker, a database or a cache
     * @return the cluster {@link #findClusterName(String, String)} finds among all clusters, whatever their kinds, or
     * the address itself when it is not listed
     */
    public String clusterNameOrAddress(final String address) {
        final String cluster = findClusterName(address, null);
        return cluster == null ? address : cluster;
    }

    /**
     * @param address the address a span reports for a broker, a database or a cache: one address, or a list joined by
     *                {@code ,} or {@code ;}
     * @param kind    a kind a cluster lists in its {@code kinds}, a name the user chose such as {@code postgresql}: only
     *                the clusters that list it, or list no kind, are matched; null matches every cluster
     * @return the cluster the whole string, or every element of the list in any order, is listed under; null when the
     * address is not listed, or its elements are listed under different clusters or not at all
     */
    public String findClusterName(final String address, final String kind) {
        final Clusters current = clusters;
        if (current.isEmpty() || StringUtil.isBlank(address)) {
            return null;
        }
        final String whole = current.find(kind, address.trim());
        if (whole != null) {
            return whole;
        }
        String cluster = null;
        for (final String element : LIST_SEPARATORS.split(address)) {
            final String trimmed = element.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final String found = current.findElement(kind, trimmed);
            if (found == null || cluster != null && !cluster.equals(found)) {
                return null;
            }
            cluster = found;
        }
        return cluster;
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            rawConfig = null;
            clusters = fileClusters;
            log.info("Server clusters reset to {}", CONFIG_FILE_NAME);
            return;
        }
        // value() reports the item as received even when it is rejected, so the configuration register does not
        // send the same invalid content again on every sync.
        rawConfig = value.getNewValue();
        try {
            clusters = parse(rawConfig);
            log.info("Server clusters updated from the dynamic configuration");
        } catch (final IllegalArgumentException e) {
            log.error("Server clusters unchanged, the dynamic configuration is invalid: {}", e.getMessage());
        }
    }

    @Override
    public String value() {
        return rawConfig;
    }

    private Clusters loadFile() {
        final Reader reader;
        try {
            reader = ResourceUtils.read(CONFIG_FILE_NAME);
        } catch (final FileNotFoundException e) {
            log.info("{} not found, no server cluster is configured", CONFIG_FILE_NAME);
            return Clusters.EMPTY;
        }
        try (Reader content = reader) {
            return build(new Yaml(new SafeConstructor(new LoaderOptions())).load(content));
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot read " + CONFIG_FILE_NAME, e);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(CONFIG_FILE_NAME + " is invalid: " + e.getMessage(), e);
        }
    }

    Clusters parse(final String content) {
        if (StringUtil.isBlank(content)) {
            return Clusters.EMPTY;
        }
        return build(new Yaml(new SafeConstructor(new LoaderOptions())).load(content));
    }

    private Clusters build(final Object root) {
        if (root == null) {
            return Clusters.EMPTY;
        }
        final Map<String, Object> top = asMap(root, "the file");
        rejectUnknownKeys(top, Set.of("clusters"), "the file");
        final Map<String, String> byAddress = new HashMap<>();
        final Map<String, String> ofEveryKind = new HashMap<>();
        final Map<String, Map<String, String>> byAddressOfKind = new HashMap<>();
        final Map<String, String> schemeByCluster = new HashMap<>();
        final Set<String> names = new HashSet<>();
        final Object clusterList = top.get("clusters");
        if (clusterList != null) {
            if (!(clusterList instanceof List)) {
                throw new IllegalArgumentException("clusters must be a list");
            }
            for (final Object item : (List<?>) clusterList) {
                final Map<String, Object> cluster = asMap(item, "a cluster");
                rejectUnknownKeys(cluster, Set.of("name", "kinds", "scheme", "members", "aliases"), "a cluster");
                final Object nameValue = cluster.get("name");
                if (!(nameValue instanceof String) || StringUtil.isBlank((String) nameValue)) {
                    throw new IllegalArgumentException("a cluster has no name");
                }
                final String name = ((String) nameValue).trim();
                if (name.length() > maxNameLength) {
                    throw new IllegalArgumentException("cluster name '" + name + "' is longer than the "
                        + maxNameLength + " characters a service name keeps");
                }
                if (!names.add(name)) {
                    throw new IllegalArgumentException("cluster '" + name + "' is listed twice");
                }
                final Object scheme = cluster.get("scheme");
                if (scheme != null) {
                    if (!(scheme instanceof String) || StringUtil.isBlank((String) scheme)) {
                        throw new IllegalArgumentException("cluster '" + name
                            + "' has a scheme that is not the prefix its clients write, such as pulsar://");
                    }
                    schemeByCluster.put(name, ((String) scheme).trim());
                }
                final List<String> kinds = asStrings(cluster.get("kinds"), name + ".kinds");
                final List<String> addresses = new ArrayList<>(asStrings(cluster.get("members"), name + ".members"));
                addresses.addAll(asStrings(cluster.get("aliases"), name + ".aliases"));
                for (final String address : addresses) {
                    final String previous = byAddress.putIfAbsent(address, name);
                    if (previous != null) {
                        throw new IllegalArgumentException(
                            "address '" + address + "' is listed twice, under '" + previous + "' and '" + name + "'");
                    }
                    if (kinds.isEmpty()) {
                        ofEveryKind.put(address, name);
                    }
                    for (final String kind : kinds) {
                        byAddressOfKind.computeIfAbsent(kind, k -> new HashMap<>()).put(address, name);
                    }
                }
            }
        }
        final Map<String, Map<String, String>> index = new HashMap<>();
        byAddressOfKind.forEach((kind, addresses) -> index.put(kind, Collections.unmodifiableMap(addresses)));
        return new Clusters(Collections.unmodifiableMap(byAddress), Collections.unmodifiableMap(index),
                            Collections.unmodifiableMap(ofEveryKind), Collections.unmodifiableMap(schemeByCluster));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value, final String what) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(what + " must be a mapping");
        }
        return (Map<String, Object>) value;
    }

    private static void rejectUnknownKeys(final Map<String, Object> map, final Set<String> known, final String what) {
        for (final String key : map.keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException(what + " has an unknown key '" + key + "'");
            }
        }
    }

    private static List<String> asStrings(final Object value, final String what) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(what + " must be a list");
        }
        final List<String> strings = new ArrayList<>();
        for (final Object item : (List<?>) value) {
            // YAML reads an unquoted `on` as a boolean and `[h1, h2]` as a list; their text is not what was written.
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(what + " has an entry that is not a string, " + item);
            }
            if (StringUtil.isBlank((String) item)) {
                throw new IllegalArgumentException(what + " has a blank entry");
            }
            strings.add(((String) item).trim());
        }
        return strings;
    }

    /**
     * One validated configuration, swapped as a whole, so a lookup never mixes two of them.
     */
    static final class Clusters {
        static final Clusters EMPTY = new Clusters(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        /**
         * The addresses of every cluster.
         */
        private final Map<String, String> byAddress;
        /**
         * Per kind, the addresses of the clusters that list that kind.
         */
        private final Map<String, Map<String, String>> byAddressOfKind;
        /**
         * The addresses of the clusters that list no kind, matched whatever kind is asked for.
         */
        private final Map<String, String> ofEveryKind;
        private final Map<String, String> schemeByCluster;
        /**
         * The schemes the clusters declare, longest first, so a scheme that begins another is tried after it.
         */
        private final List<String> schemes;

        private Clusters(final Map<String, String> byAddress,
                         final Map<String, Map<String, String>> byAddressOfKind,
                         final Map<String, String> ofEveryKind,
                         final Map<String, String> schemeByCluster) {
            this.byAddress = byAddress;
            this.byAddressOfKind = byAddressOfKind;
            this.ofEveryKind = ofEveryKind;
            this.schemeByCluster = schemeByCluster;
            this.schemes = schemeByCluster.values().stream().distinct()
                                          .sorted(Comparator.comparingInt(String::length).reversed())
                                          .collect(Collectors.toList());
        }

        private boolean isEmpty() {
            return byAddress.isEmpty();
        }

        /**
         * @param kind the kind being looked up, null for every cluster
         * @return the cluster an address is listed under, among those that list the kind or list no kind
         */
        private String find(final String kind, final String address) {
            if (kind == null) {
                return byAddress.get(address);
            }
            final String cluster = byAddressOfKind.getOrDefault(kind, Collections.emptyMap()).get(address);
            return cluster != null ? cluster : ofEveryKind.get(address);
        }

        /**
         * @return the cluster of one list element: as reported, or, for an element that begins with a declared
         * scheme, the cluster that lists the rest of it and declares exactly that scheme
         */
        private String findElement(final String kind, final String element) {
            final String literal = find(kind, element);
            if (literal != null) {
                return literal;
            }
            for (final String scheme : schemes) {
                if (element.length() > scheme.length() && element.startsWith(scheme)) {
                    final String cluster = find(kind, element.substring(scheme.length()));
                    if (cluster != null && scheme.equals(schemeByCluster.get(cluster))) {
                        return cluster;
                    }
                }
            }
            return null;
        }
    }
}
