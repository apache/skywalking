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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.ProcessNode;
import org.apache.skywalking.oap.server.core.query.type.ProcessTopology;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads the relations a topology is built from, the way the topology query service does, through
 * {@link TopologyQueryEsDAO}: the service relations of the whole range or of some services, the instance relations
 * of a service pair, the relations of an endpoint and the process relations of an instance, each from the model of
 * the side that detected it, and the service and process topologies the query service builds from them. A nested
 * class of a server's IT names the server and the storage mode.
 *
 * <p>The range is the minute before midnight and the minute of midnight, so every read spans two daily indices, and
 * relations lie on both its bounds and in the minutes just outside them. The server and the client side each detect
 * relations the other does not, and in the default mode both sides share the merged metrics index, so a read that
 * lost its model filter returns the other side's relations. The relation models have no routing column: the sharded
 * mode routes them by id over both shards, and the reads search every shard. A service or process relation may name
 * another component in each minute, a service relation several in one minute, and each component is a call of its
 * own. Every filter has a near miss: a relation of a service, an instance, an endpoint or a process not asked for,
 * an instance relation from one service of the pair to a third one, an endpoint of the same name on another
 * service, and relations rolled up per hour in the same index, which a minute read must not return and an hour read
 * must. A scheduler service and process relation each name another component in each of twelve minutes, more than
 * a terms aggregation keeps by default.
 *
 * <p>The topology builders key the calls by id, so a read is compared as the set of calls it returns.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TopologyQueryITBase {
    /** A fixed local midnight, so the daily indices the relations are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final int TOMCAT = 1;
    private static final int MYSQL = 5;
    private static final int GRPC = 23;
    private static final int HTTP = 49;
    /** Readable names of the ids the fixture builds, to print the calls read. */
    private static final Map<String, String> NAMES = new HashMap<>();

    private static final String USER = service("User", false);
    private static final String GATEWAY = service("gateway", true);
    private static final String CHECKOUT = service("checkout", true);
    private static final String PAYMENT = service("payment", true);
    private static final String LEDGER = service("ledger", true);
    private static final String AUDIT = service("audit", true);
    private static final String ORDERS_DB = service("orders-db:3306", false);

    private static final String GATEWAY_1 = instance(GATEWAY, "gateway-1");
    private static final String GATEWAY_2 = instance(GATEWAY, "gateway-2");
    private static final String CHECKOUT_1 = instance(CHECKOUT, "checkout-1");
    private static final String PAYMENT_1 = instance(PAYMENT, "payment-1");

    private static final String CHECKOUT_1_ENVOY = process(CHECKOUT_1, "envoy");
    private static final String CHECKOUT_1_JAVA = process(CHECKOUT_1, "java");
    private static final String CHECKOUT_1_PYTHON = process(CHECKOUT_1, "python");
    private static final String PAYMENT_1_ENVOY = process(PAYMENT_1, "envoy");
    private static final String PAYMENT_1_JAVA = process(PAYMENT_1, "java");

    private static final String SCHEDULER = service("scheduler", true);
    private static final String SCHEDULER_1 = instance(SCHEDULER, "scheduler-1");
    private static final String SCHEDULER_1_CRON = process(SCHEDULER_1, "cron");
    private static final String SCHEDULER_1_WORKER = process(SCHEDULER_1, "worker");
    /** The first of the minutes in which a scheduler relation names another component each. */
    private static final int FIRST_COMPONENT_MINUTE = 10;
    /** One component per minute, more than a terms aggregation keeps by default. */
    private static final int COMPONENTS = 12;
    /** The names the component library gives the component ids of the fixture. */
    private static final Map<Integer, String> COMPONENT_NAMES = componentNames();

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private TopologyQueryEsDAO dao;
    private TopologyQueryService topologyQueryService;

    TopologyQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeRelations() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        // As at boot, every model is installed before anything is written: a daily index is created from the index
        // template at its first write, and maps only the columns the template had then.
        final Model services = support.installMetrics(ServiceRelationServerSideMetrics.class);
        final Model hourlyServices = support.installMetrics(ServiceRelationServerSideMetrics.class, DownSampling.Hour);
        final Model servicesAtClient = support.installMetrics(ServiceRelationClientSideMetrics.class);
        final Model instances = support.installMetrics(ServiceInstanceRelationServerSideMetrics.class);
        final Model instancesAtClient = support.installMetrics(ServiceInstanceRelationClientSideMetrics.class);
        final Model endpoints = support.installMetrics(EndpointRelationServerSideMetrics.class);
        final Model processes = support.installMetrics(ProcessRelationServerSideMetrics.class);
        final Model processesAtClient = support.installMetrics(ProcessRelationClientSideMetrics.class);
        final Model processTrafficModel = support.installMetrics(ProcessTraffic.class);

        support.writeMetrics(
            services,
            new ServiceRelationServerSideMetrics.Builder(),
            Arrays.asList(
                serverSide(USER, GATEWAY, minute(-1), HTTP),
                serverSide(USER, GATEWAY, minute(0), HTTP),
                serverSide(GATEWAY, CHECKOUT, minute(-1), HTTP),
                serverSide(CHECKOUT, PAYMENT, minute(-1), HTTP),
                serverSide(CHECKOUT, PAYMENT, minute(0), GRPC),
                serverSide(PAYMENT, CHECKOUT, minute(0), GRPC, HTTP),
                serverSide(LEDGER, AUDIT, minute(-2), HTTP),
                serverSide(LEDGER, AUDIT, minute(1), HTTP)));
        support.writeMetrics(
            hourlyServices,
            new ServiceRelationServerSideMetrics.Builder(),
            Arrays.asList(
                serverSide(AUDIT, GATEWAY, hour(-1), HTTP),
                serverSide(GATEWAY, AUDIT, hour(0), TOMCAT),
                serverSide(CHECKOUT, PAYMENT, hour(-1), GRPC),
                serverSide(LEDGER, GATEWAY, hour(-2), HTTP),
                serverSide(LEDGER, GATEWAY, hour(1), HTTP)));
        support.writeMetrics(
            servicesAtClient,
            new ServiceRelationClientSideMetrics.Builder(),
            Arrays.asList(
                clientSide(GATEWAY, CHECKOUT, minute(0), HTTP),
                clientSide(CHECKOUT, PAYMENT, minute(-1), HTTP),
                clientSide(CHECKOUT, PAYMENT, minute(0), HTTP, GRPC),
                clientSide(PAYMENT, CHECKOUT, minute(0), GRPC),
                clientSide(CHECKOUT, ORDERS_DB, minute(-1), MYSQL),
                clientSide(CHECKOUT, ORDERS_DB, minute(0), MYSQL),
                clientSide(LEDGER, AUDIT, minute(-2), HTTP),
                clientSide(LEDGER, AUDIT, minute(1), HTTP)));

        support.writeMetrics(
            instances,
            new ServiceInstanceRelationServerSideMetrics.Builder(),
            Arrays.asList(
                instanceServerSide(GATEWAY_1, CHECKOUT_1, -1),
                instanceServerSide(GATEWAY_2, CHECKOUT_1, 0),
                instanceServerSide(CHECKOUT_1, GATEWAY_1, 0),
                instanceServerSide(GATEWAY_1, PAYMENT_1, -1),
                instanceServerSide(PAYMENT_1, CHECKOUT_1, 0),
                instanceServerSide(CHECKOUT_1, GATEWAY_2, -2),
                instanceServerSide(CHECKOUT_1, GATEWAY_2, 1)));
        support.writeMetrics(
            instancesAtClient,
            new ServiceInstanceRelationClientSideMetrics.Builder(),
            Arrays.asList(
                instanceClientSide(GATEWAY_1, CHECKOUT_1, -1),
                instanceClientSide(GATEWAY_1, CHECKOUT_1, 0),
                instanceClientSide(CHECKOUT_1, GATEWAY_2, 0),
                instanceClientSide(PAYMENT_1, CHECKOUT_1, -1),
                instanceClientSide(GATEWAY_2, CHECKOUT_1, -2),
                instanceClientSide(GATEWAY_2, CHECKOUT_1, 1)));

        support.writeMetrics(
            endpoints,
            new EndpointRelationServerSideMetrics.Builder(),
            Arrays.asList(
                endpointRelation(GATEWAY, "/checkout", CHECKOUT, "/pay", -1),
                endpointRelation(CHECKOUT, "/pay", PAYMENT, "/charge", 0),
                endpointRelation(GATEWAY, "/cart", CHECKOUT, "/stock", -1),
                endpointRelation(GATEWAY, "/cart", CHECKOUT, "/stock", 0),
                endpointRelation(GATEWAY, "/pay", PAYMENT, "/charge", 0),
                endpointRelation(LEDGER, "/refund", CHECKOUT, "/pay", -2),
                endpointRelation(LEDGER, "/refund", CHECKOUT, "/pay", 1)));

        support.writeMetrics(
            processes,
            new ProcessRelationServerSideMetrics.Builder(),
            Arrays.asList(
                processServerSide(CHECKOUT_1, CHECKOUT_1_ENVOY, CHECKOUT_1_JAVA, -1, HTTP),
                processServerSide(CHECKOUT_1, CHECKOUT_1_ENVOY, CHECKOUT_1_JAVA, 0, HTTP),
                processServerSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_ENVOY, -1, GRPC),
                processServerSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_ENVOY, 0, HTTP),
                processServerSide(PAYMENT_1, PAYMENT_1_ENVOY, PAYMENT_1_JAVA, 0, HTTP),
                processServerSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_PYTHON, -2, HTTP),
                processServerSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_PYTHON, 1, HTTP)));
        support.writeMetrics(
            processesAtClient,
            new ProcessRelationClientSideMetrics.Builder(),
            Arrays.asList(
                processClientSide(CHECKOUT_1, CHECKOUT_1_ENVOY, CHECKOUT_1_JAVA, -1, HTTP),
                processClientSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_PYTHON, 0, GRPC),
                processClientSide(PAYMENT_1, PAYMENT_1_ENVOY, PAYMENT_1_JAVA, -1, HTTP),
                processClientSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_ENVOY, -2, HTTP),
                processClientSide(CHECKOUT_1, CHECKOUT_1_JAVA, CHECKOUT_1_ENVOY, 1, HTTP)));

        final List<ServiceRelationServerSideMetrics> schedulerCalls = new ArrayList<>();
        final List<ServiceRelationClientSideMetrics> schedulerCallsAtClient = new ArrayList<>();
        final List<ProcessRelationServerSideMetrics> cronCalls = new ArrayList<>();
        final List<ProcessRelationClientSideMetrics> cronCallsAtClient = new ArrayList<>();
        for (int component = 1; component <= COMPONENTS; component++) {
            final int minute = FIRST_COMPONENT_MINUTE + component - 1;
            schedulerCalls.add(serverSide(SCHEDULER, LEDGER, minute(minute), component));
            schedulerCallsAtClient.add(clientSide(SCHEDULER, LEDGER, minute(minute), component));
            cronCalls.add(processServerSide(SCHEDULER_1, SCHEDULER_1_CRON, SCHEDULER_1_WORKER, minute, component));
            cronCallsAtClient.add(
                processClientSide(SCHEDULER_1, SCHEDULER_1_CRON, SCHEDULER_1_WORKER, minute, component));
        }
        support.writeMetrics(services, new ServiceRelationServerSideMetrics.Builder(), schedulerCalls);
        support.writeMetrics(servicesAtClient, new ServiceRelationClientSideMetrics.Builder(), schedulerCallsAtClient);
        support.writeMetrics(processes, new ProcessRelationServerSideMetrics.Builder(), cronCalls);
        support.writeMetrics(processesAtClient, new ProcessRelationClientSideMetrics.Builder(), cronCallsAtClient);

        support.writeMetrics(
            processTrafficModel,
            new ProcessTraffic.Builder(),
            Arrays.asList(
                processTraffic(CHECKOUT_1, "envoy", -1),
                processTraffic(CHECKOUT_1, "java", -1),
                processTraffic(CHECKOUT_1, "python", 0),
                processTraffic(PAYMENT_1, "envoy", 0),
                processTraffic(PAYMENT_1, "java", 0),
                processTraffic(SCHEDULER_1, "cron", -1),
                processTraffic(SCHEDULER_1, "worker", 0)));

        dao = new TopologyQueryEsDAO(support.getClient());
        topologyQueryService = topologyQueryService(new StorageEsDAO(support.getClient()), processTrafficModel);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsEveryServiceRelationDetectedAtTheServerSide() throws Exception {
        assertEquals(
            set(
                "checkout -> payment #23 SERVER",
                "checkout -> payment #49 SERVER",
                "gateway -> checkout #49 SERVER",
                "payment -> checkout #23 SERVER",
                "payment -> checkout #49 SERVER",
                "User -> gateway #49 SERVER"),
            calls(dao.loadServiceRelationsDetectedAtServerSide(minutes())));
    }

    @Test
    public void readsEveryServiceRelationDetectedAtTheClientSide() throws Exception {
        assertEquals(
            set(
                "checkout -> orders-db:3306 #5 CLIENT",
                "checkout -> payment #23 CLIENT",
                "checkout -> payment #49 CLIENT",
                "gateway -> checkout #49 CLIENT",
                "payment -> checkout #23 CLIENT"),
            calls(dao.loadServiceRelationDetectedAtClientSide(minutes())));
    }

    @Test
    public void readsTheServiceRelationsOfOneServiceEitherWay() throws Exception {
        final List<String> services = Collections.singletonList(CHECKOUT);
        assertEquals(
            set(
                "checkout -> payment #23 SERVER",
                "checkout -> payment #49 SERVER",
                "gateway -> checkout #49 SERVER",
                "payment -> checkout #23 SERVER",
                "payment -> checkout #49 SERVER"),
            calls(dao.loadServiceRelationsDetectedAtServerSide(minutes(), services)));
        assertEquals(
            set(
                "checkout -> orders-db:3306 #5 CLIENT",
                "checkout -> payment #23 CLIENT",
                "checkout -> payment #49 CLIENT",
                "gateway -> checkout #49 CLIENT",
                "payment -> checkout #23 CLIENT"),
            calls(dao.loadServiceRelationDetectedAtClientSide(minutes(), services)));
    }

    @Test
    public void readsTheServiceRelationsOfSomeServices() throws Exception {
        assertEquals(
            set(
                "gateway -> checkout #49 SERVER",
                "User -> gateway #49 SERVER"),
            calls(dao.loadServiceRelationsDetectedAtServerSide(minutes(), Arrays.asList(GATEWAY, ORDERS_DB))));
        assertEquals(
            set(
                "checkout -> orders-db:3306 #5 CLIENT",
                "gateway -> checkout #49 CLIENT"),
            calls(dao.loadServiceRelationDetectedAtClientSide(minutes(), Arrays.asList(GATEWAY, ORDERS_DB))));
    }

    @Test
    public void readsNoServiceRelationOfServicesThatOnlyCalledOutsideTheRange() throws Exception {
        final List<String> services = Arrays.asList(LEDGER, AUDIT);
        assertEquals(set(), calls(dao.loadServiceRelationsDetectedAtServerSide(minutes(), services)));
        assertEquals(set(), calls(dao.loadServiceRelationDetectedAtClientSide(minutes(), services)));
    }

    @Test
    public void readsTheHourlyServiceRelationsAtAnHourStep() throws Exception {
        assertEquals(
            set(
                "audit -> gateway #49 SERVER",
                "checkout -> payment #23 SERVER",
                "gateway -> audit #1 SERVER"),
            calls(dao.loadServiceRelationsDetectedAtServerSide(hours())));
        assertEquals(
            set(
                "audit -> gateway #49 SERVER",
                "gateway -> audit #1 SERVER"),
            calls(dao.loadServiceRelationsDetectedAtServerSide(hours(), Collections.singletonList(GATEWAY))));
    }

    @Test
    public void readsTheInstanceRelationsOfAServicePairBothWays() throws Exception {
        final Set<String> serverSide = set(
            "checkout-1 -> gateway-1 #null SERVER",
            "gateway-1 -> checkout-1 #null SERVER",
            "gateway-2 -> checkout-1 #null SERVER");
        assertEquals(serverSide, calls(dao.loadInstanceRelationDetectedAtServerSide(GATEWAY, CHECKOUT, minutes())));
        assertEquals(serverSide, calls(dao.loadInstanceRelationDetectedAtServerSide(CHECKOUT, GATEWAY, minutes())));

        final Set<String> clientSide = set(
            "checkout-1 -> gateway-2 #null CLIENT",
            "gateway-1 -> checkout-1 #null CLIENT");
        assertEquals(clientSide, calls(dao.loadInstanceRelationDetectedAtClientSide(GATEWAY, CHECKOUT, minutes())));
        assertEquals(clientSide, calls(dao.loadInstanceRelationDetectedAtClientSide(CHECKOUT, GATEWAY, minutes())));
    }

    @Test
    public void readsTheRelationsOfAnEndpointEitherWay() throws Exception {
        assertEquals(
            set(
                "checkout:/pay -> payment:/charge #0 SERVER",
                "gateway:/checkout -> checkout:/pay #0 SERVER"),
            calls(dao.loadEndpointRelation(minutes(), endpoint(CHECKOUT, "/pay"))));
        assertEquals(
            set("gateway:/cart -> checkout:/stock #0 SERVER"),
            calls(dao.loadEndpointRelation(minutes(), endpoint(GATEWAY, "/cart"))));
        assertEquals(set(), calls(dao.loadEndpointRelation(minutes(), endpoint(LEDGER, "/refund"))));
    }

    @Test
    public void readsTheProcessRelationsOfAnInstance() throws Exception {
        assertEquals(
            set(
                "checkout-1/envoy -> checkout-1/java #49 SERVER",
                "checkout-1/java -> checkout-1/envoy #23 SERVER",
                "checkout-1/java -> checkout-1/envoy #49 SERVER"),
            calls(dao.loadProcessRelationDetectedAtServerSide(CHECKOUT_1, minutes())));
        assertEquals(
            set(
                "checkout-1/envoy -> checkout-1/java #49 CLIENT",
                "checkout-1/java -> checkout-1/python #23 CLIENT"),
            calls(dao.loadProcessRelationDetectedAtClientSide(CHECKOUT_1, minutes())));
        assertEquals(
            set("payment-1/envoy -> payment-1/java #49 SERVER"),
            calls(dao.loadProcessRelationDetectedAtServerSide(PAYMENT_1, minutes())));
    }

    @Test
    public void readsEveryComponentOfARelationThatChangesItEveryMinute() throws Exception {
        final Duration range = componentMinutes();
        final Set<String> services = new TreeSet<>();
        final Set<String> servicesAtClient = new TreeSet<>();
        final Set<String> processes = new TreeSet<>();
        final Set<String> processesAtClient = new TreeSet<>();
        for (int component = 1; component <= COMPONENTS; component++) {
            services.add("scheduler -> ledger #" + component + " SERVER");
            servicesAtClient.add("scheduler -> ledger #" + component + " CLIENT");
            processes.add("scheduler-1/cron -> scheduler-1/worker #" + component + " SERVER");
            processesAtClient.add("scheduler-1/cron -> scheduler-1/worker #" + component + " CLIENT");
        }
        assertEquals(services, calls(dao.loadServiceRelationsDetectedAtServerSide(range)));
        assertEquals(services, calls(dao.loadServiceRelationsDetectedAtServerSide(
            range, Collections.singletonList(SCHEDULER))));
        assertEquals(servicesAtClient, calls(dao.loadServiceRelationDetectedAtClientSide(range)));
        assertEquals(processes, calls(dao.loadProcessRelationDetectedAtServerSide(SCHEDULER_1, range)));
        assertEquals(processesAtClient, calls(dao.loadProcessRelationDetectedAtClientSide(SCHEDULER_1, range)));
    }

    /**
     * A built call keeps every component its relation named in the range: the client side's as its source
     * components, the server side's as its target components. No network address alias and no service is known, so
     * no call is folded onto another service.
     */
    @Test
    public void buildsTheServiceTopologyWithEveryComponentOfBothSides() throws Exception {
        assertEquals(
            sorted(
                "User -> gateway [SERVER] [] [http]",
                "checkout -> orders-db:3306 [CLIENT] [Mysql] []",
                "checkout -> payment [CLIENT, SERVER] [GRPC, http] [GRPC, http]",
                "gateway -> checkout [CLIENT, SERVER] [http] [http]",
                "payment -> checkout [CLIENT, SERVER] [GRPC] [GRPC, http]"),
            builtCalls(topologyQueryService.getGlobalTopology(minutes(), null).getCalls()));
        assertEquals(
            sorted("scheduler -> ledger [CLIENT, SERVER] " + library() + " " + library()),
            builtCalls(topologyQueryService.getGlobalTopology(componentMinutes(), null).getCalls()));
    }

    @Test
    public void buildsTheProcessTopologyWithEveryComponentOfBothSides() throws Exception {
        final ProcessTopology checkout = topologyQueryService.getProcessTopology(CHECKOUT_1, minutes());
        assertEquals(
            sorted(
                "checkout-1/envoy -> checkout-1/java [CLIENT, SERVER] [http] [http]",
                "checkout-1/java -> checkout-1/envoy [SERVER] [] [GRPC, http]",
                "checkout-1/java -> checkout-1/python [CLIENT] [GRPC] []"),
            builtCalls(checkout.getCalls()));
        assertEquals(
            sorted("checkout-1/envoy of checkout", "checkout-1/java of checkout", "checkout-1/python of checkout"),
            processNodes(checkout.getNodes()));

        final ProcessTopology scheduler = topologyQueryService.getProcessTopology(SCHEDULER_1, componentMinutes());
        assertEquals(
            sorted("scheduler-1/cron -> scheduler-1/worker [CLIENT, SERVER] " + library() + " " + library()),
            builtCalls(scheduler.getCalls()));
        assertEquals(
            sorted("scheduler-1/cron of scheduler", "scheduler-1/worker of scheduler"),
            processNodes(scheduler.getNodes()));
    }

    /**
     * The calls read, as {@code source -> target #component detect point}. A call whose id is not the relation of
     * its source and target is printed with its id.
     */
    private static Set<String> calls(final List<Call.CallDetail> calls) {
        return calls.stream().map(call -> {
            final String relation = name(call.getSource()) + " -> " + name(call.getTarget());
            final String id = relation.equals(name(call.getId())) ? "" : " id " + name(call.getId());
            return relation + " #" + call.getComponentId() + " " + call.getDetectPoint() + id;
        }).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The calls of a built topology, sorted, as {@code source -> target [detect points] [source components] [target
     * components]}. A call whose id is not the relation of its source and target is printed with its id.
     */
    private static List<String> builtCalls(final List<Call> calls) {
        return calls.stream().map(call -> {
            final String relation = name(call.getSource()) + " -> " + name(call.getTarget());
            final String id = relation.equals(name(call.getId())) ? "" : " id " + name(call.getId());
            return relation + " " + sortedValues(call.getDetectPoints())
                + " " + sortedValues(call.getSourceComponents())
                + " " + sortedValues(call.getTargetComponents()) + id;
        }).sorted().collect(Collectors.toList());
    }

    /** The nodes of a process topology, sorted, as {@code instance/process of service}. */
    private static List<String> processNodes(final List<ProcessNode> nodes) {
        return nodes.stream()
                    .map(node -> node.getServiceInstanceName() + "/" + node.getName() + " of " + node.getServiceName()
                        + (name(node.getId()).equals(node.getServiceInstanceName() + "/" + node.getName())
                        ? "" : " id " + name(node.getId()))
                        + (node.isReal() ? "" : " virtual"))
                    .sorted()
                    .collect(Collectors.toList());
    }

    private static List<String> sortedValues(final List<?> values) {
        return values.stream().map(String::valueOf).sorted().collect(Collectors.toList());
    }

    /** The names of the components the scheduler relations name, one per minute. */
    private static List<String> library() {
        return sortedValues(
            IntStream.rangeClosed(1, COMPONENTS).mapToObj(COMPONENT_NAMES::get).collect(Collectors.toList()));
    }

    private static List<String> sorted(final String... values) {
        return Arrays.stream(values).sorted().collect(Collectors.toList());
    }

    private static Map<Integer, String> componentNames() {
        final Map<Integer, String> names = new HashMap<>();
        final String[] library = {
            "Tomcat", "HttpClient", "Dubbo", "H2", "Mysql", "ORACLE", "Redis", "Motan", "MongoDB", "Resin", "Feign",
            "OKHttp"
        };
        for (int i = 0; i < library.length; i++) {
            names.put(i + 1, library[i]);
        }
        names.put(GRPC, "GRPC");
        names.put(HTTP, "http");
        return names;
    }

    /**
     * The topology query service over the DAO under test, with OAP's storage DAO to read the process traffic.
     */
    private TopologyQueryService topologyQueryService(final StorageDAO storageDAO, final Model processTraffic) {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder storage = services(moduleManager, StorageModule.NAME);
        when(storage.getService(ITopologyQueryDAO.class)).thenReturn(dao);
        when(storage.getService(StorageDAO.class)).thenReturn(storageDAO);
        final IComponentLibraryCatalogService components = mock(IComponentLibraryCatalogService.class);
        when(components.getComponentName(anyInt())).thenAnswer(
            invocation -> COMPONENT_NAMES.get(invocation.<Integer>getArgument(0)));
        final ModuleServiceHolder core = services(moduleManager, CoreModule.NAME);
        when(core.getService(IComponentLibraryCatalogService.class)).thenReturn(components);
        when(core.getService(NetworkAddressAliasCache.class)).thenReturn(
            new NetworkAddressAliasCache(new CoreModuleConfig()));
        when(core.getService(MetadataQueryService.class)).thenReturn(mock(MetadataQueryService.class));
        final StorageModels models = mock(StorageModels.class);
        when(models.allModels()).thenReturn(Collections.singletonList(processTraffic));
        return new TopologyQueryService(moduleManager, models);
    }

    private static ModuleServiceHolder services(final ModuleManager moduleManager, final String moduleName) {
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(moduleName)).thenReturn(provider);
        when(provider.provider()).thenReturn(services);
        return services;
    }

    private static Set<String> set(final String... calls) {
        return new TreeSet<>(Arrays.asList(calls));
    }

    private static String name(final String id) {
        return NAMES.getOrDefault(id, id);
    }

    private static Duration minutes() {
        return duration(MIDNIGHT - 60_000L, MIDNIGHT, Step.MINUTE, "yyyy-MM-dd HHmm");
    }

    private static Duration minutes(final long first, final long last) {
        return duration(MIDNIGHT + first * 60_000L, MIDNIGHT + last * 60_000L, Step.MINUTE, "yyyy-MM-dd HHmm");
    }

    private static Duration componentMinutes() {
        return minutes(FIRST_COMPONENT_MINUTE, FIRST_COMPONENT_MINUTE + COMPONENTS - 1);
    }

    private static Duration hours() {
        return duration(MIDNIGHT - 3_600_000L, MIDNIGHT, Step.HOUR, "yyyy-MM-dd HH");
    }

    private static Duration duration(final long start, final long end, final Step step, final String pattern) {
        final Duration duration = new Duration();
        duration.setStep(step);
        duration.setStart(new DateTime(start).toString(pattern));
        duration.setEnd(new DateTime(end).toString(pattern));
        return duration;
    }

    private static long minute(final long minute) {
        return TimeBucket.getMinuteTimeBucket(MIDNIGHT + minute * 60_000L);
    }

    private static long hour(final long hour) {
        return TimeBucket.getTimeBucket(MIDNIGHT + hour * 3_600_000L, DownSampling.Hour);
    }

    private static String service(final String name, final boolean normal) {
        final String id = IDManager.ServiceID.buildId(name, normal);
        NAMES.put(id, name);
        return id;
    }

    private static String instance(final String serviceId, final String name) {
        final String id = IDManager.ServiceInstanceID.buildId(serviceId, name);
        NAMES.put(id, name);
        return id;
    }

    private static String endpoint(final String serviceId, final String name) {
        final String id = IDManager.EndpointID.buildId(serviceId, name);
        NAMES.put(id, NAMES.get(serviceId) + ":" + name);
        return id;
    }

    private static String process(final String instanceId, final String name) {
        final String id = IDManager.ProcessID.buildId(instanceId, name);
        NAMES.put(id, NAMES.get(instanceId) + "/" + name);
        return id;
    }

    private static String relation(final String relationId, final String source, final String dest) {
        NAMES.put(relationId, name(source) + " -> " + name(dest));
        return relationId;
    }

    private static String serviceRelation(final String source, final String dest) {
        return relation(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(source, dest)), source, dest);
    }

    private static String instanceRelation(final String source, final String dest) {
        return relation(IDManager.ServiceInstanceID.buildRelationId(
            new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(source, dest)), source, dest);
    }

    private static String processRelation(final String source, final String dest) {
        return relation(IDManager.ProcessID.buildRelationId(
            new IDManager.ProcessID.ProcessRelationDefine(source, dest)), source, dest);
    }

    private static ProcessTraffic processTraffic(final String instanceId, final String name,
                                                 final long firstSeenMinute) {
        final ProcessTraffic process = new ProcessTraffic();
        process.setServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        process.setInstanceId(instanceId);
        process.setName(name);
        process.setAgentId("rover-1");
        process.setDetectType(ProcessDetectType.VM.value());
        process.setProfilingSupportStatus(ProfilingSupportStatus.SUPPORT_EBPF_PROFILING.value());
        process.setLabelsJson(Const.EMPTY_STRING);
        process.setTimeBucket(minute(firstSeenMinute));
        process.setLastPingTimestamp(minute(firstSeenMinute));
        return process;
    }

    private static IntList components(final int... ids) {
        final IntList components = new IntList(ids.length);
        for (final int id : ids) {
            components.add(id);
        }
        return components;
    }

    private static ServiceRelationServerSideMetrics serverSide(final String source, final String dest,
                                                               final long timeBucket, final int... components) {
        final ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setSourceServiceId(source);
        metrics.setDestServiceId(dest);
        metrics.setComponentIds(components(components));
        metrics.setEntityId(serviceRelation(source, dest));
        metrics.setTimeBucket(timeBucket);
        return metrics;
    }

    private static ServiceRelationClientSideMetrics clientSide(final String source, final String dest,
                                                               final long timeBucket, final int... components) {
        final ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setSourceServiceId(source);
        metrics.setDestServiceId(dest);
        metrics.setComponentIds(components(components));
        metrics.setEntityId(serviceRelation(source, dest));
        metrics.setTimeBucket(timeBucket);
        return metrics;
    }

    private static ServiceInstanceRelationServerSideMetrics instanceServerSide(final String source, final String dest,
                                                                               final long minute) {
        final ServiceInstanceRelationServerSideMetrics metrics = new ServiceInstanceRelationServerSideMetrics();
        metrics.setSourceServiceId(IDManager.ServiceInstanceID.analysisId(source).getServiceId());
        metrics.setSourceServiceInstanceId(source);
        metrics.setDestServiceId(IDManager.ServiceInstanceID.analysisId(dest).getServiceId());
        metrics.setDestServiceInstanceId(dest);
        metrics.setEntityId(instanceRelation(source, dest));
        metrics.setTimeBucket(minute(minute));
        return metrics;
    }

    private static ServiceInstanceRelationClientSideMetrics instanceClientSide(final String source, final String dest,
                                                                               final long minute) {
        final ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setSourceServiceId(IDManager.ServiceInstanceID.analysisId(source).getServiceId());
        metrics.setSourceServiceInstanceId(source);
        metrics.setDestServiceId(IDManager.ServiceInstanceID.analysisId(dest).getServiceId());
        metrics.setDestServiceInstanceId(dest);
        metrics.setEntityId(instanceRelation(source, dest));
        metrics.setTimeBucket(minute(minute));
        return metrics;
    }

    private static EndpointRelationServerSideMetrics endpointRelation(final String sourceService,
                                                                      final String source,
                                                                      final String destService,
                                                                      final String dest,
                                                                      final long minute) {
        final EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        final String sourceId = endpoint(sourceService, source);
        final String destId = endpoint(destService, dest);
        metrics.setSourceEndpoint(sourceId);
        metrics.setDestEndpoint(destId);
        metrics.setComponentId(HTTP);
        metrics.setEntityId(relation(
            IDManager.EndpointID.buildRelationId(
                new IDManager.EndpointID.EndpointRelationDefine(sourceService, source, destService, dest)),
            sourceId, destId));
        metrics.setTimeBucket(minute(minute));
        return metrics;
    }

    private static ProcessRelationServerSideMetrics processServerSide(final String instance, final String source,
                                                                      final String dest, final long minute,
                                                                      final int component) {
        final ProcessRelationServerSideMetrics metrics = new ProcessRelationServerSideMetrics();
        metrics.setServiceInstanceId(instance);
        metrics.setSourceProcessId(source);
        metrics.setDestProcessId(dest);
        metrics.setComponentId(component);
        metrics.setEntityId(processRelation(source, dest));
        metrics.setTimeBucket(minute(minute));
        return metrics;
    }

    private static ProcessRelationClientSideMetrics processClientSide(final String instance, final String source,
                                                                      final String dest, final long minute,
                                                                      final int component) {
        final ProcessRelationClientSideMetrics metrics = new ProcessRelationClientSideMetrics();
        metrics.setServiceInstanceId(instance);
        metrics.setSourceProcessId(source);
        metrics.setDestProcessId(dest);
        metrics.setComponentId(component);
        metrics.setEntityId(processRelation(source, dest));
        metrics.setTimeBucket(minute(minute));
        return metrics;
    }
}
