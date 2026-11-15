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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetadataQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads services, instances, endpoints and processes the way the metadata query service does, on the index-mode
 * measures OAP installs for them. Each row is created at one minute and last seen at another, so a read by a time
 * window must apply both bounds: created no later than the window's end, last seen no earlier than its start.
 */
@Testcontainers
public class BanyanDBMetadataQueryIT {
    /** More services than BanyanDB returns without an explicit limit (100). */
    private static final int SERVICES = 120;
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("metadata-it", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("metadata-it-other", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-0");
    private static final String AGENT = "agent-0";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBMetadataQueryDAO QUERY_DAO;
    private static long FIRST_MINUTE;

    @BeforeAll
    public static void writeMetadata() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        FIRST_MINUTE = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3)) / 60_000L * 60_000L;

        final List<ServiceTraffic> services = new ArrayList<>();
        for (int i = 0; i < SERVICES; i++) {
            services.add(service("metadata-it-" + i, i % 2 == 0 ? Layer.GENERAL : Layer.MESH));
        }
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceTraffic.class, DefaultScopeDefine.SERVICE, ServiceTraffic.INDEX_NAME),
                             new ServiceTraffic.Builder(), services);

        final var instanceModel = SUPPORT.installMetrics(
            InstanceTraffic.class, DefaultScopeDefine.SERVICE_INSTANCE, InstanceTraffic.INDEX_NAME);
        SUPPORT.writeMetrics(instanceModel, new InstanceTraffic.Builder(), Arrays.asList(
            instance(SERVICE_ID, "created-early-seen-late", 0, 100),
            instance(SERVICE_ID, "created-late", 100, 100),
            instance(SERVICE_ID, "seen-early", 0, 10),
            instance(SERVICE_ID, "updated", 0, 10),
            instance(OTHER_SERVICE_ID, "other-service", 0, 100)));
        // The metrics worker writes an instance again when it pings, with its creation minute kept.
        SUPPORT.updateMetrics(instanceModel, new InstanceTraffic.Builder(),
                              Collections.singletonList(instance(SERVICE_ID, "updated", 0, 105)));

        SUPPORT.writeMetrics(SUPPORT.installMetrics(EndpointTraffic.class, DefaultScopeDefine.ENDPOINT, EndpointTraffic.INDEX_NAME),
                             new EndpointTraffic.Builder(), Arrays.asList(
                endpoint(SERVICE_ID, "GET:/api/orders", 0, 100),
                endpoint(SERVICE_ID, "POST:/api/orders", 1, 100),
                endpoint(SERVICE_ID, "GET:/api/users", 2, 100),
                endpoint(SERVICE_ID, "GET:/api/orders/archive", 3, 10),
                endpoint(OTHER_SERVICE_ID, "GET:/api/orders", 4, 100)));

        final JsonObject sameProperties = new JsonObject();
        sameProperties.addProperty("host_name", "node-0");
        final JsonObject otherProperties = new JsonObject();
        otherProperties.addProperty("host_name", "node-1");
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ProcessTraffic.class, DefaultScopeDefine.PROCESS, ProcessTraffic.INDEX_NAME),
                             new ProcessTraffic.Builder(), Arrays.asList(
                // Two processes with the same properties are still two processes.
                process("supported-0", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, sameProperties, 0, 100),
                process("supported-1", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, sameProperties, 0, 100),
                process("virtual", ProcessDetectType.VIRTUAL, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, otherProperties, 0, 100),
                process("not-supported", ProcessDetectType.VM, ProfilingSupportStatus.NOT_SUPPORT, otherProperties, 0, 100),
                process("created-late", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, sameProperties, 115, 115),
                process("seen-early", ProcessDetectType.VM, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, otherProperties, 0, 10)));

        QUERY_DAO = new BanyanDBMetadataQueryDAO(SUPPORT.getClient(), SUPPORT.getConfig());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(SERVICES, QUERY_DAO.listServices().size());
            assertEquals(6, QUERY_DAO.listProcesses(INSTANCE_ID, minutes(0, 120), true).size());
            assertEquals(Arrays.asList("created-early-seen-late", "created-late", "updated"),
                         instanceNames(QUERY_DAO.listInstances(minutes(90, 110), SERVICE_ID)));
        });
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void listsEveryService() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = 0; i < SERVICES; i++) {
            expected.add("metadata-it-" + i + "|" + (i % 2 == 0 ? "GENERAL" : "MESH"));
        }
        Collections.sort(expected);
        assertEquals(expected, QUERY_DAO.listServices().stream()
            .map(s -> s.getName() + "|" + String.join(",", s.getLayers())).sorted().collect(Collectors.toList()));
    }

    @Test
    public void listsTheInstancesOfAWindow() throws IOException {
        assertEquals(Arrays.asList("created-early-seen-late", "created-late", "updated"),
                     instanceNames(QUERY_DAO.listInstances(minutes(90, 110), SERVICE_ID)));
        assertEquals(Arrays.asList("created-early-seen-late", "updated"),
                     instanceNames(QUERY_DAO.listInstances(minutes(50, 60), SERVICE_ID)));
        assertEquals(Arrays.asList("created-early-seen-late", "created-late", "seen-early", "updated"),
                     instanceNames(QUERY_DAO.listInstances(null, SERVICE_ID)));
    }

    @Test
    public void readsInstancesById() throws IOException {
        final String early = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "seen-early");
        final String other = IDManager.ServiceInstanceID.buildId(OTHER_SERVICE_ID, "other-service");
        assertEquals("seen-early", QUERY_DAO.getInstance(early).getName());
        assertNull(QUERY_DAO.getInstance(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "missing")));
        assertEquals(Arrays.asList("other-service", "seen-early"), instanceNames(QUERY_DAO.getInstances(
            Arrays.asList(early, other, IDManager.ServiceInstanceID.buildId(SERVICE_ID, "missing")))));
    }

    @Test
    public void findsEndpointsByKeyword() throws IOException {
        assertEquals(Arrays.asList("GET:/api/orders", "POST:/api/orders"),
                     sortedEndpointNames(QUERY_DAO.findEndpoint("orders", SERVICE_ID, 10, minutes(90, 110))));
        assertEquals(Arrays.asList("GET:/api/orders", "GET:/api/orders/archive", "POST:/api/orders"),
                     sortedEndpointNames(QUERY_DAO.findEndpoint("orders", SERVICE_ID, 10, null)));
        assertEquals(Collections.singletonList("GET:/api/orders/archive"),
                     sortedEndpointNames(QUERY_DAO.findEndpoint("orders archive", SERVICE_ID, 10, null)));
    }

    @Test
    @Disabled("BanyanDB ignores timestamp ORDER BY on an index-mode measure: apache/skywalking#14218")
    public void findsTheNewestEndpointsUpToTheLimit() throws IOException {
        assertEquals(Arrays.asList("GET:/api/users", "POST:/api/orders"),
                     endpointNames(QUERY_DAO.findEndpoint(null, SERVICE_ID, 2, minutes(90, 110))));
    }

    @Test
    public void listsTheProcessesOfAService() throws IOException {
        assertEquals(Arrays.asList("supported-0", "supported-1"), processNames(QUERY_DAO.listProcesses(
            SERVICE_ID, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, minuteBucket(90), minuteBucket(110))));
    }

    @Test
    public void listsTheProcessesOfAnInstance() throws IOException {
        assertEquals(Arrays.asList("not-supported", "supported-0", "supported-1"),
                     processNames(QUERY_DAO.listProcesses(INSTANCE_ID, minutes(90, 110), false)));
        assertEquals(Arrays.asList("not-supported", "supported-0", "supported-1", "virtual"),
                     processNames(QUERY_DAO.listProcesses(INSTANCE_ID, minutes(90, 110), true)));
    }

    @Test
    public void listsTheProcessesOfAnAgent() throws IOException {
        assertEquals(Arrays.asList("not-supported", "supported-0", "supported-1"),
                     processNames(QUERY_DAO.listProcesses(AGENT, minuteBucket(90), minuteBucket(110))));
    }

    @Test
    public void countsProcesses() throws IOException {
        assertEquals(2, QUERY_DAO.getProcessCount(
            SERVICE_ID, ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, minuteBucket(90), minuteBucket(110)));
        assertEquals(5, QUERY_DAO.getProcessCount(INSTANCE_ID));
    }

    @Test
    public void readsAProcessById() throws IOException {
        final Process process = QUERY_DAO.getProcess(IDManager.ProcessID.buildId(INSTANCE_ID, "supported-1"));
        assertEquals("supported-1|" + SERVICE_ID + "|" + INSTANCE_ID + "|" + AGENT + "|VM|SUPPORT_EBPF_PROFILING",
                     String.join("|", process.getName(), process.getServiceId(), process.getInstanceId(),
                                 process.getAgentId(), process.getDetectType(), process.getProfilingSupportStatus()));
        assertNull(QUERY_DAO.getProcess(IDManager.ProcessID.buildId(INSTANCE_ID, "missing")));
    }

    private static List<String> instanceNames(Collection<ServiceInstance> instances) {
        return instances.stream().map(ServiceInstance::getName).sorted().collect(Collectors.toList());
    }

    private static List<String> endpointNames(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::getName).collect(Collectors.toList());
    }

    private static List<String> sortedEndpointNames(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::getName).sorted().collect(Collectors.toList());
    }

    private static List<String> processNames(List<Process> processes) {
        return processes.stream().map(Process::getName).sorted().collect(Collectors.toList());
    }

    private static Duration minutes(int first, int last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + first * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + last * 60_000L));
        return duration;
    }

    private static long minuteBucket(int minute) {
        return TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + minute * 60_000L);
    }

    private static ServiceTraffic service(String name, Layer layer) {
        final ServiceTraffic service = new ServiceTraffic();
        service.setName(name);
        service.setShortName(name);
        service.setLayer(layer);
        service.setServiceId(IDManager.ServiceID.buildId(name, true));
        service.setTimeBucket(minuteBucket(0));
        return service;
    }

    private static InstanceTraffic instance(String serviceId, String name, int created, int lastSeen) {
        final InstanceTraffic instance = new InstanceTraffic();
        instance.setServiceId(serviceId);
        instance.setName(name);
        instance.setProperties(new JsonObject());
        instance.setTimeBucket(minuteBucket(created));
        instance.setLastPingTimestamp(minuteBucket(lastSeen));
        return instance;
    }

    private static EndpointTraffic endpoint(String serviceId, String name, int created, int lastSeen) {
        final EndpointTraffic endpoint = new EndpointTraffic();
        endpoint.setServiceId(serviceId);
        endpoint.setName(name);
        endpoint.setTimeBucket(minuteBucket(created));
        endpoint.setLastPingTimestamp(minuteBucket(lastSeen));
        return endpoint;
    }

    private static ProcessTraffic process(String name, ProcessDetectType detectType, ProfilingSupportStatus status,
                                          JsonObject properties, int created, int lastSeen) {
        final ProcessTraffic process = new ProcessTraffic();
        process.setServiceId(SERVICE_ID);
        process.setInstanceId(INSTANCE_ID);
        process.setName(name);
        process.setAgentId(AGENT);
        process.setDetectType(detectType.value());
        process.setProfilingSupportStatus(status.value());
        process.setProperties(properties);
        process.setLabelsJson("[]");
        process.setTimeBucket(minuteBucket(created));
        process.setLastPingTimestamp(minuteBucket(lastSeen));
        return process;
    }
}
