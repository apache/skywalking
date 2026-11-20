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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressAliasEsDAO;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads services, instances, endpoints, the processes an eBPF agent reports, the labels of a service's processes,
 * network address aliases and searchable tags on Elasticsearch and OpenSearch, the way the metadata query, the eBPF
 * receiver and profiling, the alias cache and tag autocompletion do. A nested class of a server's IT names the server
 * and the storage mode.
 *
 * <p>Traffic is stored in the daily index of the minute it was first seen, on both sides of midnight here, and read
 * through the alias of every day; a document written again (an instance that pings, an alias that changes) is updated
 * in that index. A row is created at one minute and last seen at another, so a read by a window applies both bounds:
 * created no later than the window's end, last seen no earlier than its start. The window most reads use crosses
 * midnight; rows sit on each of its bounds and one minute outside, and rows of another service, instance or agent
 * match every other condition. Endpoints are found newest first. The lists are scrolled two documents a page.
 *
 * <p>A tag is stored once for each day it is seen, and autocompletion reads the indices of the days the window
 * touches, whatever its minutes: tags seen on both days, only on one, three days before and of the other tag type.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MetadataQueryITBase {
    private static final Gson GSON = new Gson();
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    /** Fewer than every list holds, so each list is read over more than one scroll page. */
    private static final int SCROLLING_BATCH_SIZE = 2;
    private static final String ORDERS = "orders";
    private static final String ORDERS_ID = IDManager.ServiceID.buildId(ORDERS, true);
    private static final String PAYMENT = "finance::payment";
    private static final String PAYMENT_ID = IDManager.ServiceID.buildId(PAYMENT, true);
    private static final String DATABASE = "mysql.orders:3306";
    private static final String ORDERS_INSTANCE = "orders-1";
    private static final String ORDERS_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(ORDERS_ID, ORDERS_INSTANCE);
    private static final String PAYMENT_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(PAYMENT_ID, "payment-1");
    private static final String ORDERS_AGENT = "rover-node-2";
    /** The first and the last minute, counted from midnight, of the window most reads use. */
    private static final int WINDOW_START = -10;
    private static final int WINDOW_END = 10;
    /** Searchable tags as type, key, value and the minute, counted from midnight, they were seen. */
    private static final String[][] TAGS = {
        {"TRACE", "http.method", "GET", "-30"},
        {"TRACE", "http.status_code", "200", "-30"},
        {"TRACE", "rpc.method", "Get", "-30"},
        {"TRACE", "http.route", "/a", "-30"},
        {"TRACE", "http.route", "/b", "-30"},
        {"TRACE", "http.method", "GET", "20"},
        {"TRACE", "http.method", "POST", "20"},
        {"TRACE", "http.status_code", "500", "20"},
        {"TRACE", "db.type", "MySQL", "20"},
        {"TRACE", "http.route", "/a", "20"},
        {"LOG", "level", "ERROR", "20"},
        {"LOG", "http.method", "PUT", "20"},
        {"TRACE", "old.key", "old", String.valueOf(-3 * 24 * 60)},
    };

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private final Map<String, String> instanceRows = new HashMap<>();
    private final Map<String, String> processRows = new HashMap<>();
    private ElasticSearchQueryITSupport support;
    private MetadataQueryEsDAO metadataDAO;
    private TagAutoCompleteQueryDAO tagDAO;
    private NetworkAddressAliasEsDAO aliasDAO;
    private ServiceLabelEsDAO labelDAO;
    private Duration window;
    private Duration aroundMidnight;
    private Duration afterMidnight;

    MetadataQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeTraffic() throws Exception {
        support = ElasticSearchQueryITSupport.connect(
            server, mode, getClass(), config -> config.setScrollingBatchSize(SCROLLING_BATCH_SIZE));
        // Every model is installed before the first write, as at boot: in the default mode they share the daily
        // indices of one physical index, and an index created before a model was installed lacks its mappings.
        final Model serviceModel = support.installMetrics(ServiceTraffic.class);
        final Model instanceModel = support.installMetrics(InstanceTraffic.class);
        final Model endpointModel = support.installMetrics(EndpointTraffic.class);
        final Model processModel = support.installMetrics(ProcessTraffic.class);
        final Model tagModel = support.installMetrics(TagAutocompleteData.class);
        final Model aliasModel = support.installMetrics(NetworkAddressAlias.class);
        final Model labelModel = support.installMetrics(ServiceLabelRecord.class);

        support.writeMetrics(serviceModel, new ServiceTraffic.Builder(), Arrays.asList(
            service(ORDERS, Layer.GENERAL, -60),
            service(ORDERS, Layer.MESH, 5),
            service(PAYMENT, Layer.GENERAL, 5),
            service(DATABASE, Layer.VIRTUAL_DATABASE, -60)));

        support.writeMetrics(instanceModel, new InstanceTraffic.Builder(), Arrays.asList(
            ordersInstance("seen-across-midnight", -60, 30, "java", "hostname", "orders-host-0"),
            ordersInstance("pinged-at-start", -60, WINDOW_START, "java"),
            ordersInstance("pinged-before-start", -60, WINDOW_START - 1, "java"),
            ordersInstance("created-at-end", WINDOW_END, 30, null),
            ordersInstance("updated", -60, -30, "java"),
            instance(PAYMENT_ID, "payment-1", -60, 30, properties("python"))));
        // The metrics worker writes an instance again when it pings, with its first minute kept.
        support.updateMetrics(instanceModel, new InstanceTraffic.Builder(),
                              Collections.singletonList(ordersInstance("updated", -60, 5, "java")));

        support.writeMetrics(endpointModel, new EndpointTraffic.Builder(), Arrays.asList(
            endpoint(ORDERS_ID, "GET:/api/orders", -60, 30),
            endpoint(ORDERS_ID, "GET:/api/orders/archive", -61, WINDOW_START - 1),
            endpoint(ORDERS_ID, "PATCH:/api/orders", -59, WINDOW_START),
            endpoint(ORDERS_ID, "POST:/api/orders", -5, 30),
            endpoint(ORDERS_ID, "GET:/api/users", 5, 30),
            endpoint(ORDERS_ID, "DELETE:/api/orders", WINDOW_END, WINDOW_END),
            endpoint(PAYMENT_ID, "GET:/api/orders", -60, 30)));

        final JsonObject host = new JsonObject();
        host.addProperty("host_name", "node-0");
        host.addProperty("pid", "1001");
        support.writeMetrics(processModel, new ProcessTraffic.Builder(), Arrays.asList(
            ordersProcess("first-seen-yesterday", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          -60, 30, host, "env:prod", "team:a"),
            ordersProcess("first-seen-today", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          5, 30, null),
            ordersProcess("virtual", ProcessDetectType.VIRTUAL, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          -60, 30, null),
            ordersProcess("not-supported", ProcessDetectType.KUBERNETES, ProfilingSupportStatus.NOT_SUPPORT,
                          -60, 30, null),
            ordersProcess("pinged-at-start", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          -60, WINDOW_START, null),
            ordersProcess("pinged-before-start", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          -60, WINDOW_START - 1, null),
            ordersProcess("created-at-end", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          WINDOW_END, WINDOW_END, null),
            ordersProcess("created-after-end", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING,
                          WINDOW_END + 1, 30, null),
            process(PAYMENT_ID, PAYMENT_INSTANCE_ID, "rover-node-3", "payment-process", ProcessDetectType.VM,
                    ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, -60, 30, null)));

        final List<TagAutocompleteData> tags = new ArrayList<>();
        for (final String[] tag : TAGS) {
            tags.add(tag(tag));
        }
        support.writeMetrics(tagModel, new TagAutocompleteData.Builder(), tags);

        support.writeMetrics(aliasModel, new NetworkAddressAlias.Builder(), Arrays.asList(
            alias("10.0.0.1:80", ORDERS_INSTANCE_ID, -60, -30),
            alias("10.0.0.2:80", ORDERS_INSTANCE_ID, -60, WINDOW_START),
            alias("10.0.0.3:80", ORDERS_INSTANCE_ID, -60, WINDOW_START - 1),
            alias("10.0.0.4:80", PAYMENT_INSTANCE_ID, 5, 5)));
        // An alias set up again by another instance is updated in place, with its first minute kept.
        support.updateMetrics(aliasModel, new NetworkAddressAlias.Builder(),
                              Collections.singletonList(alias("10.0.0.1:80", PAYMENT_INSTANCE_ID, -60, 5)));

        support.writeMetrics(labelModel, new ServiceLabelRecord.Builder(), Arrays.asList(
            label(ORDERS_ID, "env:prod", -60), label(ORDERS_ID, "team:a", 5), label(PAYMENT_ID, "env:test", -60)));

        metadataDAO = new MetadataQueryEsDAO(support.getClient(), support.getConfig());
        tagDAO = new TagAutoCompleteQueryDAO(support.getClient());
        aliasDAO = new NetworkAddressAliasEsDAO(support.getClient(), support.getConfig());
        labelDAO = new ServiceLabelEsDAO(support.getClient(), support.getConfig());
        window = duration(minute(WINDOW_START), minute(WINDOW_END), Step.MINUTE);
        aroundMidnight = duration(minute(-30), minute(30), Step.MINUTE);
        afterMidnight = duration(minute(10), minute(30), Step.MINUTE);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void listsEveryServiceOfEveryDay() {
        assertEquals(
            new TreeSet<>(Arrays.asList(
                ORDERS_ID + "|orders|orders|null|GENERAL|true",
                ORDERS_ID + "|orders|orders|null|MESH|true",
                PAYMENT_ID + "|finance::payment|payment|finance|GENERAL|true",
                IDManager.ServiceID.buildId(DATABASE, false) + "|" + DATABASE + "|" + DATABASE
                    + "|null|VIRTUAL_DATABASE|false")),
            serviceRows(metadataDAO.listServices()));
    }

    @Test
    public void limitsTheServicesToTheMetadataQueryMaxSize() {
        final StorageModuleElasticsearchConfig config = new StorageModuleElasticsearchConfig();
        config.setMetadataQueryMaxSize(3);
        config.setScrollingBatchSize(SCROLLING_BATCH_SIZE);
        final Set<String> limited = serviceRows(
            new MetadataQueryEsDAO(support.getClient(), config).listServices());
        assertEquals(3, limited.size());
        assertTrue(serviceRows(metadataDAO.listServices()).containsAll(limited));
    }

    @Test
    public void listsTheInstancesOfAServiceAliveInTheWindow() {
        assertEquals(expectedInstances("seen-across-midnight", "pinged-at-start", "created-at-end", "updated"),
                     instanceRows(metadataDAO.listInstances(window, ORDERS_ID)));
    }

    @Test
    public void listsEveryInstanceOfAServiceWithoutAWindow() {
        assertEquals(expectedInstances("seen-across-midnight", "pinged-at-start", "pinged-before-start",
                                       "created-at-end", "updated"),
                     instanceRows(metadataDAO.listInstances(null, ORDERS_ID)));
    }

    @Test
    public void readsAnInstanceByIdFromEitherDay() {
        assertEquals(instanceRows.get("seen-across-midnight"),
                     instanceRow(metadataDAO.getInstance(ordersInstanceId("seen-across-midnight"))));
        assertEquals(instanceRows.get("created-at-end"),
                     instanceRow(metadataDAO.getInstance(ordersInstanceId("created-at-end"))));
        assertNull(metadataDAO.getInstance(ordersInstanceId("missing")));
    }

    @Test
    public void readsTheInstancesOfTheIds() {
        assertEquals(expectedInstances("seen-across-midnight", "created-at-end", "updated"),
                     instanceRows(metadataDAO.getInstances(Arrays.asList(
                         ordersInstanceId("seen-across-midnight"), ordersInstanceId("created-at-end"),
                         ordersInstanceId("updated"), ordersInstanceId("missing")))));
    }

    @Test
    public void findsTheEndpointsMatchingAKeywordNewestFirst() {
        assertEquals(endpointRows("DELETE:/api/orders", "POST:/api/orders", "PATCH:/api/orders", "GET:/api/orders"),
                     endpointRows(metadataDAO.findEndpoint("orders", ORDERS_ID, 10, window)));
        assertEquals(endpointRows("DELETE:/api/orders", "POST:/api/orders", "PATCH:/api/orders", "GET:/api/orders",
                                  "GET:/api/orders/archive"),
                     endpointRows(metadataDAO.findEndpoint("orders", ORDERS_ID, 10, null)));
        // The keyword is matched against the words of the analyzed name.
        assertEquals(endpointRows("GET:/api/orders/archive"),
                     endpointRows(metadataDAO.findEndpoint("archive", ORDERS_ID, 10, null)));
    }

    @Test
    public void findsTheNewestEndpointsUpToTheLimit() {
        assertEquals(endpointRows("DELETE:/api/orders", "GET:/api/users"),
                     endpointRows(metadataDAO.findEndpoint(null, ORDERS_ID, 2, window)));
        assertEquals(endpointRows("DELETE:/api/orders", "GET:/api/users", "POST:/api/orders"),
                     endpointRows(metadataDAO.findEndpoint(null, ORDERS_ID, 3, window)));
    }

    /**
     * As the eBPF profiling and continuous profiling read them: profiling-supported or every non-virtual process
     * alive in the window, or, without an end, every one pinging since the start.
     */
    @Test
    public void listsTheProcessesOfAServiceAliveInTheWindow() {
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "pinged-at-start", "created-at-end"),
                     processRows(metadataDAO.listProcesses(
                         ORDERS_ID, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, bucket(WINDOW_START),
                         bucket(WINDOW_END))));
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "not-supported", "pinged-at-start",
                                       "created-at-end"),
                     processRows(metadataDAO.listProcesses(ORDERS_ID, null, bucket(WINDOW_START), bucket(WINDOW_END))));
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "not-supported", "pinged-at-start",
                                       "created-at-end", "created-after-end"),
                     processRows(metadataDAO.listProcesses(ORDERS_ID, null, bucket(WINDOW_START), 0)));
    }

    @Test
    public void listsTheProcessesOfAnInstanceAliveInTheWindow() {
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "not-supported", "pinged-at-start",
                                       "created-at-end"),
                     processRows(metadataDAO.listProcesses(ORDERS_INSTANCE_ID, window, false)));
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "virtual", "not-supported",
                                       "pinged-at-start", "created-at-end"),
                     processRows(metadataDAO.listProcesses(ORDERS_INSTANCE_ID, window, true)));
    }

    @Test
    public void listsTheProcessesOfAnAgentAliveInTheWindow() {
        assertEquals(expectedProcesses("first-seen-yesterday", "first-seen-today", "not-supported", "pinged-at-start",
                                       "created-at-end"),
                     processRows(metadataDAO.listProcesses(ORDERS_AGENT, bucket(WINDOW_START), bucket(WINDOW_END))));
    }

    @Test
    public void countsTheProcessesOfAServiceAndOfAnInstance() {
        assertEquals(4, metadataDAO.getProcessCount(
            ORDERS_ID, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, bucket(WINDOW_START), bucket(WINDOW_END)));
        // Every non-virtual process of the instance, whenever it pinged.
        assertEquals(7, metadataDAO.getProcessCount(ORDERS_INSTANCE_ID));
    }

    @Test
    public void readsAProcessByIdFromEitherDay() {
        assertEquals(
            String.join("|", IDManager.ProcessID.buildId(ORDERS_INSTANCE_ID, "first-seen-yesterday"),
                        "first-seen-yesterday", ORDERS_ID, ORDERS, ORDERS_INSTANCE_ID, ORDERS_INSTANCE, ORDERS_AGENT,
                        "VM", "SUPPORT_EBPF_PROFILING", "host_name=node-0,pid=1001", "env:prod,team:a"),
            processRow(metadataDAO.getProcess(
                IDManager.ProcessID.buildId(ORDERS_INSTANCE_ID, "first-seen-yesterday"))));
        assertEquals(processRows.get("first-seen-today"),
                     processRow(metadataDAO.getProcess(
                         IDManager.ProcessID.buildId(ORDERS_INSTANCE_ID, "first-seen-today"))));
        assertNull(metadataDAO.getProcess(IDManager.ProcessID.buildId(ORDERS_INSTANCE_ID, "missing")));
    }

    /**
     * As each node's alias cache reloads them: every alias whose last update is on or after a minute, from the index
     * of the day it was first set up.
     */
    @Test
    public void loadsTheAliasesUpdatedSinceAMinute() {
        assertEquals(
            Arrays.asList(
                "10.0.0.1:80|" + PAYMENT_ID + "|" + PAYMENT_INSTANCE_ID + "|" + bucket(5) + "|" + bucket(-60),
                "10.0.0.2:80|" + ORDERS_ID + "|" + ORDERS_INSTANCE_ID + "|" + bucket(WINDOW_START) + "|" + bucket(-60),
                "10.0.0.4:80|" + PAYMENT_ID + "|" + PAYMENT_INSTANCE_ID + "|" + bucket(5) + "|" + bucket(5)),
            aliasRows(aliasDAO.loadLastUpdate(bucket(WINDOW_START))));
        assertEquals(Arrays.asList("10.0.0.1:80", "10.0.0.4:80"),
                     aliasDAO.loadLastUpdate(bucket(5)).stream().map(NetworkAddressAlias::getAddress).sorted()
                             .collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), aliasDAO.loadLastUpdate(bucket(6)));
    }

    @Test
    public void readsTheLabelsOfAServiceFromEveryDay() {
        assertEquals(Arrays.asList("env:prod", "team:a"),
                     labelDAO.queryAllLabels(ORDERS_ID).stream().sorted().collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), labelDAO.queryAllLabels(IDManager.ServiceID.buildId("missing", true)));
    }

    @Test
    public void autocompletesTheTagKeysOfTheDaysOfTheWindow() throws Exception {
        assertEquals(new TreeSet<>(Arrays.asList("db.type", "http.method", "http.route", "http.status_code",
                                                 "rpc.method")),
                     new TreeSet<>(tagDAO.queryTagAutocompleteKeys(List.of(TagType.TRACE.name()), 10, aroundMidnight)));
        assertEquals(new TreeSet<>(Arrays.asList("db.type", "http.method", "http.route", "http.status_code")),
                     new TreeSet<>(tagDAO.queryTagAutocompleteKeys(List.of(TagType.TRACE.name()), 10, afterMidnight)));
        assertEquals(new TreeSet<>(Arrays.asList("http.method", "level")),
                     new TreeSet<>(tagDAO.queryTagAutocompleteKeys(List.of(TagType.LOG.name()), 10, aroundMidnight)));

        final Set<String> limited = tagDAO.queryTagAutocompleteKeys(List.of(TagType.TRACE.name()), 2, aroundMidnight);
        assertEquals(2, limited.size());
        assertTrue(Arrays.asList("db.type", "http.method", "http.route", "http.status_code", "rpc.method")
                         .containsAll(limited));
    }

    @Test
    public void autocompletesTheTagValuesOfTheDaysOfTheWindow() throws Exception {
        assertEquals(new TreeSet<>(Arrays.asList("GET", "POST")),
                     new TreeSet<>(tagDAO.queryTagAutocompleteValues(
                         List.of(TagType.TRACE.name()), "http.method", 10, aroundMidnight)));
        assertEquals(new TreeSet<>(Arrays.asList("200", "500")),
                     new TreeSet<>(tagDAO.queryTagAutocompleteValues(
                         List.of(TagType.TRACE.name()), "http.status_code", 10, aroundMidnight)));
        assertEquals(Collections.singleton("500"),
                     tagDAO.queryTagAutocompleteValues(List.of(TagType.TRACE.name()), "http.status_code", 10, afterMidnight));
        assertEquals(Collections.singleton("PUT"),
                     tagDAO.queryTagAutocompleteValues(List.of(TagType.LOG.name()), "http.method", 10, aroundMidnight));
        assertEquals(Collections.emptySet(),
                     tagDAO.queryTagAutocompleteValues(List.of(TagType.TRACE.name()), "old.key", 10, aroundMidnight));
    }

    private Set<String> expectedInstances(final String... names) {
        return Arrays.stream(names).map(instanceRows::get).collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> expectedProcesses(final String... names) {
        return Arrays.stream(names).map(processRows::get).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> serviceRows(final List<Service> services) {
        return services.stream()
                       .map(s -> String.join("|", s.getId(), s.getName(), s.getShortName(),
                                             String.valueOf(s.getGroup()),
                                             String.join(",", new TreeSet<>(s.getLayers())),
                                             String.valueOf(s.isNormal())))
                       .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> instanceRows(final List<ServiceInstance> instances) {
        return instances.stream().map(MetadataQueryITBase::instanceRow)
                        .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String instanceRow(final ServiceInstance instance) {
        return String.join("|", instance.getId(), instance.getName(), instance.getInstanceUUID(),
                           instance.getLanguage().name(), attributes(instance.getAttributes()));
    }

    private static List<String> endpointRows(final String... names) {
        return Arrays.stream(names)
                     .map(name -> IDManager.EndpointID.buildId(ORDERS_ID, name) + "|" + name)
                     .collect(Collectors.toList());
    }

    private static List<String> endpointRows(final List<Endpoint> endpoints) {
        return endpoints.stream().map(e -> e.getId() + "|" + e.getName()).collect(Collectors.toList());
    }

    private static Set<String> processRows(final List<Process> processes) {
        return processes.stream().map(MetadataQueryITBase::processRow)
                        .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String processRow(final Process process) {
        return String.join("|", process.getId(), process.getName(), process.getServiceId(), process.getServiceName(),
                           process.getInstanceId(), process.getInstanceName(), process.getAgentId(),
                           process.getDetectType(), process.getProfilingSupportStatus(),
                           attributes(process.getAttributes()), String.join(",", process.getLabels()));
    }

    private static List<String> aliasRows(final List<NetworkAddressAlias> aliases) {
        return aliases.stream()
                      .map(a -> String.join("|", a.getAddress(), a.getRepresentServiceId(),
                                            a.getRepresentServiceInstanceId(),
                                            String.valueOf(a.getLastUpdateTimeBucket()),
                                            String.valueOf(a.getTimeBucket())))
                      .sorted()
                      .collect(Collectors.toList());
    }

    private static String attributes(final Collection<Attribute> attributes) {
        return attributes.stream().map(a -> a.getName() + "=" + a.getValue()).collect(Collectors.joining(","));
    }

    private static long minute(final int minutesFromMidnight) {
        return MIDNIGHT + minutesFromMidnight * 60_000L;
    }

    private static long bucket(final int minutesFromMidnight) {
        return TimeBucket.getMinuteTimeBucket(minute(minutesFromMidnight));
    }

    /**
     * A query duration from {@code start} to {@code end}, as the query protocol writes it for {@code step}.
     */
    private static Duration duration(final long start, final long end, final Step step) {
        final String pattern;
        switch (step) {
            case MINUTE:
                pattern = "yyyy-MM-dd HHmm";
                break;
            case HOUR:
                pattern = "yyyy-MM-dd HH";
                break;
            default:
                pattern = "yyyy-MM-dd";
                break;
        }
        final Duration duration = new Duration();
        duration.setStep(step);
        duration.setStart(new DateTime(start).toString(pattern));
        duration.setEnd(new DateTime(end).toString(pattern));
        return duration;
    }

    private static String ordersInstanceId(final String name) {
        return IDManager.ServiceInstanceID.buildId(ORDERS_ID, name);
    }

    private static ServiceTraffic service(final String name, final Layer layer, final int created) {
        final ServiceTraffic service = new ServiceTraffic();
        service.setName(name);
        service.setLayer(layer);
        service.setTimeBucket(bucket(created));
        return service;
    }

    private static InstanceTraffic instance(final String serviceId, final String name, final int created,
                                            final int lastPing, final JsonObject properties) {
        final InstanceTraffic instance = new InstanceTraffic();
        instance.setServiceId(serviceId);
        instance.setName(name);
        instance.setProperties(properties);
        instance.setTimeBucket(bucket(created));
        instance.setLastPingTimestamp(bucket(lastPing));
        return instance;
    }

    /**
     * An instance of the orders service, and the row the metadata query reads for it.
     *
     * @param language   the language the agent reports, or null for an instance that reports no properties
     * @param attributes the other properties, as key and value
     */
    private InstanceTraffic ordersInstance(final String name, final int created, final int lastPing,
                                           final String language, final String... attributes) {
        final List<Attribute> expected = new ArrayList<>();
        for (int i = 0; i < attributes.length; i += 2) {
            expected.add(new Attribute(attributes[i], attributes[i + 1]));
        }
        final String id = ordersInstanceId(name);
        instanceRows.put(name, String.join("|", id, name, id, language == null ? "UNKNOWN" : language.toUpperCase(),
                                           attributes(expected)));
        return instance(ORDERS_ID, name, created, lastPing, language == null ? null : properties(language, attributes));
    }

    private static JsonObject properties(final String language, final String... attributes) {
        final JsonObject properties = new JsonObject();
        properties.addProperty(InstanceTraffic.PropertyUtil.LANGUAGE, language);
        for (int i = 0; i < attributes.length; i += 2) {
            properties.addProperty(attributes[i], attributes[i + 1]);
        }
        return properties;
    }

    private static EndpointTraffic endpoint(final String serviceId, final String name, final int created,
                                            final int lastPing) {
        final EndpointTraffic endpoint = new EndpointTraffic();
        endpoint.setServiceId(serviceId);
        endpoint.setName(name);
        endpoint.setTimeBucket(bucket(created));
        endpoint.setLastPingTimestamp(bucket(lastPing));
        return endpoint;
    }

    private static ProcessTraffic process(final String serviceId, final String instanceId, final String agentId,
                                          final String name, final ProcessDetectType detectType,
                                          final ProfilingSupportStatus status, final int created, final int lastPing,
                                          final JsonObject properties, final String... labels) {
        final ProcessTraffic process = new ProcessTraffic();
        process.setServiceId(serviceId);
        process.setInstanceId(instanceId);
        process.setName(name);
        process.setAgentId(agentId);
        process.setDetectType(detectType.value());
        process.setProfilingSupportStatus(status.value());
        process.setProperties(properties);
        process.setLabelsJson(labels.length == 0 ? Const.EMPTY_STRING : GSON.toJson(labels));
        process.setTimeBucket(bucket(created));
        process.setLastPingTimestamp(bucket(lastPing));
        return process;
    }

    /**
     * A process of the orders instance, reported by its agent, and the row the metadata query reads for it.
     */
    private ProcessTraffic ordersProcess(final String name, final ProcessDetectType detectType,
                                         final ProfilingSupportStatus status, final int created, final int lastPing,
                                         final JsonObject properties, final String... labels) {
        final List<Attribute> expected = new ArrayList<>();
        if (properties != null) {
            properties.entrySet().forEach(p -> expected.add(new Attribute(p.getKey(), p.getValue().getAsString())));
        }
        processRows.put(name, String.join("|", IDManager.ProcessID.buildId(ORDERS_INSTANCE_ID, name), name, ORDERS_ID,
                                          ORDERS, ORDERS_INSTANCE_ID, ORDERS_INSTANCE, ORDERS_AGENT, detectType.name(),
                                          status.name(), attributes(expected), String.join(",", labels)));
        return process(ORDERS_ID, ORDERS_INSTANCE_ID, ORDERS_AGENT, name, detectType, status, created, lastPing,
                       properties, labels);
    }

    /**
     * A tag as the autocompletion dispatcher stores it, under the first minute of the day it was seen.
     */
    private static TagAutocompleteData tag(final String[] tag) {
        final TagAutocompleteData data = new TagAutocompleteData();
        data.setTagType(tag[0]);
        data.setTagKey(tag[1]);
        data.setTagValue(tag[2]);
        data.setTimeBucket(TimeBucket.retainToDay4MinuteBucket(bucket(Integer.parseInt(tag[3]))));
        return data;
    }

    private static NetworkAddressAlias alias(final String address, final String instanceId, final int created,
                                             final int lastUpdate) {
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        alias.setAddress(address);
        alias.setRepresentServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        alias.setRepresentServiceInstanceId(instanceId);
        alias.setLastUpdateTimeBucket(bucket(lastUpdate));
        alias.setTimeBucket(bucket(created));
        return alias;
    }

    private static ServiceLabelRecord label(final String serviceId, final String label, final int created) {
        final ServiceLabelRecord record = new ServiceLabelRecord();
        record.setServiceId(serviceId);
        record.setLabel(label);
        record.setTimeBucket(bucket(created));
        return record;
    }
}
