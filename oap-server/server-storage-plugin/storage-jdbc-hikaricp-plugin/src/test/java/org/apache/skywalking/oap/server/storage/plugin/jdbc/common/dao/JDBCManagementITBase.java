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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Manages UI templates the way the UI management API does, continuous-profiling policies the way the policy page and
 * the eBPF agents do, and runtime rules the way the runtime-rule API and its reconciler do; persists management rows
 * and updates metrics the way OAP's workers do, and inspects installed tables the way the boot and the runtime-rule
 * reconciler do. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCManagementITBase {
    private static final String GENERAL_SERVICE = "general-service";
    private static final String MESH_SERVICE = "mesh-service";
    private static final String K8S_SERVICE = "k8s-service";
    private static final String CHECKOUT_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String PAYMENT_ID = IDManager.ServiceID.buildId("payment", true);
    private static final String INVENTORY_ID = IDManager.ServiceID.buildId("inventory", true);
    /** Component ids of the Spring MVC and Feign plugins. */
    private static final int SPRING_MVC = 14;
    private static final int FEIGN = 11;

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private Model templateModel;
    private Model relationModel;
    private JDBCUITemplateManagementDAO templateDAO;

    JDBCManagementITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeTemplatesAndRelation() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        templateModel = support.install(
            UITemplate.class, DefaultScopeDefine.UI_TEMPLATE, UITemplate.INDEX_NAME, DownSampling.None);
        templateDAO = new JDBCUITemplateManagementDAO(support.getClient(), support.getTableHelper());
        for (final String id : Arrays.asList(GENERAL_SERVICE, MESH_SERVICE, K8S_SERVICE)) {
            assertTrue(templateDAO.addTemplate(setting(id, configuration(id, 1))).isStatus(), id);
        }

        relationModel = support.install(
            ServiceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
            ServiceRelationServerSideMetrics.INDEX_NAME, DownSampling.Minute);
        support.writeMetrics(
            relationModel, new ServiceRelationServerSideMetrics.Builder(),
            Collections.singletonList(relation(CHECKOUT_ID, PAYMENT_ID, SPRING_MVC)));

        support.install(
            ContinuousProfilingPolicy.class, DefaultScopeDefine.CONTINUOUS_PROFILING_POLICY,
            ContinuousProfilingPolicy.INDEX_NAME, DownSampling.None);
        support.install(RuntimeRule.class, DefaultScopeDefine.RUNTIME_RULE, RuntimeRule.INDEX_NAME, DownSampling.None);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void disablesAnAddedTemplate() throws Exception {
        final TemplateChangeStatus disabled = templateDAO.disableTemplate(GENERAL_SERVICE);
        assertTrue(disabled.isStatus(), disabled.getMessage());
        assertFalse(templateIds(false).contains(GENERAL_SERVICE));
        assertTrue(templateIds(true).contains(GENERAL_SERVICE));
        assertTrue(templateDAO.getTemplate(GENERAL_SERVICE).isDisabled());

        final TemplateChangeStatus disabledAgain = templateDAO.disableTemplate(GENERAL_SERVICE);
        assertTrue(disabledAgain.isStatus(), disabledAgain.getMessage());
    }

    /**
     * A change of a template never added is refused. A change replaces the whole template, so it also enables a
     * disabled one, the only way the API has to.
     */
    @Test
    public void changesAnExistingTemplateOnly() throws Exception {
        final TemplateChangeStatus missing =
            templateDAO.changeTemplate(setting("not-added", configuration("not-added", 1)));
        assertAll(
            () -> assertFalse(missing.isStatus()),
            () -> assertEquals("not-added", missing.getId()),
            () -> assertEquals("Can't find the template", missing.getMessage()),
            () -> assertNull(templateDAO.getTemplate("not-added"))
        );

        assertTrue(templateDAO.changeTemplate(setting(MESH_SERVICE, configuration(MESH_SERVICE, 2))).isStatus());
        assertEquals(configuration(MESH_SERVICE, 2), templateDAO.getTemplate(MESH_SERVICE).getConfiguration());

        assertTrue(templateDAO.disableTemplate(K8S_SERVICE).isStatus());
        assertTrue(templateDAO.changeTemplate(setting(K8S_SERVICE, configuration(K8S_SERVICE, 2))).isStatus());
        final DashboardConfiguration changed = templateDAO.getTemplate(K8S_SERVICE);
        assertFalse(changed.isDisabled());
        assertEquals(configuration(K8S_SERVICE, 2), changed.getConfiguration());
    }

    /**
     * An update that finds no row, as for a metric the session cache holds but the table lost, fails, so the cache
     * drops the metric and the next flush inserts it again; an update of a written row does not.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failsAnUpdateOfAMissingRowOnly() throws Exception {
        final JDBCMetricsDAO dao = new JDBCMetricsDAO(
            support.getClient(),
            (StorageBuilder<Metrics>) (StorageBuilder) new ServiceRelationServerSideMetrics.Builder());
        final ServiceRelationServerSideMetrics written = relation(CHECKOUT_ID, PAYMENT_ID, SPRING_MVC, FEIGN);
        final ServiceRelationServerSideMetrics missing = relation(CHECKOUT_ID, INVENTORY_ID, FEIGN);
        final SessionCacheCallback writtenCallback = mock(SessionCacheCallback.class);
        final SessionCacheCallback missingCallback = mock(SessionCacheCallback.class);

        support.flush(Arrays.asList(
            dao.prepareBatchUpdate(relationModel, written, writtenCallback),
            dao.prepareBatchUpdate(relationModel, missing, missingCallback)
        ));

        verify(missingCallback).onUpdateFailure();
        verify(writtenCallback, never()).onUpdateFailure();
        final List<Metrics> stored = dao.multiGet(relationModel, Arrays.asList(written, missing));
        assertEquals(1, stored.size());
        assertEquals(
            written.getComponentIds().toStorageData(),
            ((ServiceRelationServerSideMetrics) stored.get(0)).getComponentIds().toStorageData());
    }

    @Test
    public void reportsTheTableAndColumnsOfAnInstalledModel() throws Exception {
        for (final Model model : Arrays.asList(templateModel, relationModel)) {
            final var info = support.installer().isExists(model, StorageManipulationOpt.withSchemaChange());
            assertTrue(info.isAllExist(), model.getName());
            assertTrue(
                info.buildInstallInfoMsg().contains("allResourcesExist=true [tableExist=true, allColumnsExist=true]"),
                info.buildInstallInfoMsg());
        }
    }

    @Test
    public void reportsAModelWithoutATable() throws Exception {
        final Model model = support.register(
            ServiceInstanceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_INSTANCE_RELATION,
            ServiceInstanceRelationServerSideMetrics.INDEX_NAME, DownSampling.Minute);
        final var info = support.installer().isExists(model, StorageManipulationOpt.withSchemaChange());
        assertFalse(info.isAllExist());
        assertTrue(
            info.buildInstallInfoMsg().contains("allResourcesExist=false [tableExist=false, allColumnsExist=false]"),
            info.buildInstallInfoMsg());
    }

    /**
     * The table an older OAP installed before the model gained a column.
     */
    @Test
    public void reportsATableMissingAColumn() throws Exception {
        final Model model = support.install(
            ServiceRelationClientSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
            ServiceRelationClientSideMetrics.INDEX_NAME, DownSampling.Minute);
        support.getClient().execute(
            "ALTER TABLE " + TableHelper.getLatestTableForWrite(model) + " DROP COLUMN "
                + ServiceRelationClientSideMetrics.COMPONENT_IDS);
        final var info = support.installer().isExists(model, StorageManipulationOpt.withSchemaChange());
        assertFalse(info.isAllExist());
        assertTrue(
            info.buildInstallInfoMsg().contains("allResourcesExist=false [tableExist=true, allColumnsExist=false]"),
            info.buildInstallInfoMsg());
    }

    /**
     * A service has one policy: saving it again, as the policy page does on every change, replaces the whole policy.
     * The eBPF agents ask for the policies of their services, some of which have none.
     */
    @Test
    public void savesAPolicyPerServiceAndReplacesItOnTheNextSave() throws Exception {
        final JDBCContinuousProfilingPolicyDAO dao =
            new JDBCContinuousProfilingPolicyDAO(support.getClient(), support.getTableHelper());
        final String withoutPolicy = IDManager.ServiceID.buildId("policy-less", true);
        final ContinuousProfilingPolicy checkout = policy(CHECKOUT_ID, "{\"targets\":[\"ON_CPU\"]}", "uuid-1");
        final ContinuousProfilingPolicy payment = policy(PAYMENT_ID, "{\"targets\":[\"OFF_CPU\"]}", "uuid-2");
        dao.savePolicy(checkout);
        dao.savePolicy(payment);
        assertEquals(policies(checkout), policies(dao.queryPolicies(Collections.singletonList(CHECKOUT_ID))));

        final ContinuousProfilingPolicy changed = policy(CHECKOUT_ID, "{\"targets\":[\"NETWORK\"]}", "uuid-3");
        dao.savePolicy(changed);
        assertEquals(policies(changed, payment),
                     policies(dao.queryPolicies(Arrays.asList(CHECKOUT_ID, PAYMENT_ID, withoutPolicy))));
        assertEquals(policies(payment), policies(dao.queryPolicies(Collections.singletonList(PAYMENT_ID))));
        assertEquals(Collections.emptyList(), dao.queryPolicies(Collections.singletonList(withoutPolicy)));
    }

    /**
     * Runtime rules are kept by catalog and name, the same name in two catalogs being two rules. A save replaces the
     * whole rule, as {@code /addOrUpdate} and {@code /inactivate} do, and a delete removes the rule of one catalog,
     * silently when there is none, as a retried {@code /delete} does. The reconciler reads every rule, so each read
     * is compared whole, with the rules already kept.
     */
    @Test
    public void savesReplacesAndDeletesRulesByCatalogAndName() throws Exception {
        final JDBCRuntimeRuleManagementDAO dao = ruleDAO();
        final List<String> kept = rules(dao.getAll());
        final String active = RuntimeRule.STATUS_ACTIVE;
        final RuntimeRule vm = rule("otel-rules", "vm", "metricPrefix: vm", active, 1_000L);
        final RuntimeRule logVm = rule("log-mal-rules", "vm", "metricPrefix: log_vm", active, 1_001L);
        final RuntimeRule mysql = rule("otel-rules", "mysql", "metricPrefix: mysql", active, 1_002L);
        for (final RuntimeRule rule : Arrays.asList(vm, logVm, mysql)) {
            dao.save(rule);
        }
        assertEquals(rules(kept, vm, logVm, mysql), rules(dao.getAll()));

        final RuntimeRule inactiveVm =
            rule("otel-rules", "vm", "metricPrefix: vm_v2", RuntimeRule.STATUS_INACTIVE, 2_000L);
        dao.save(inactiveVm);
        assertEquals(rules(kept, inactiveVm, logVm, mysql), rules(dao.getAll()));

        dao.delete("log-mal-rules", "vm");
        assertEquals(rules(kept, inactiveVm, mysql), rules(dao.getAll()));
        dao.delete("log-mal-rules", "vm");
        dao.delete("otel-rules", "mysql");
        assertEquals(rules(kept, inactiveVm), rules(dao.getAll()));
    }

    /**
     * A rule's name is its path under the catalog, so it may hold a '/'.
     */
    @Test
    public void savesAndDeletesARuleNamedByAPath() throws Exception {
        final JDBCRuntimeRuleManagementDAO dao = ruleDAO();
        final List<String> kept = rules(dao.getAll());
        final RuntimeRule gateway = rule("otel-rules", "aws-gateway/gateway-service", "metricPrefix: aws_gateway",
                                         RuntimeRule.STATUS_ACTIVE, 3_000L);
        dao.save(gateway);
        assertEquals(rules(kept, gateway), rules(dao.getAll()));

        dao.delete("otel-rules", "aws-gateway/gateway-service");
        assertEquals(kept, rules(dao.getAll()));
    }

    private List<String> templateIds(final boolean includingDisabled) {
        return templateDAO.getAllTemplates(includingDisabled)
                          .stream()
                          .map(DashboardConfiguration::getId)
                          .collect(Collectors.toList());
    }

    private static DashboardSetting setting(final String id, final String configuration) {
        final DashboardSetting setting = new DashboardSetting();
        setting.setId(id);
        setting.setConfiguration(configuration);
        return setting;
    }

    private static String configuration(final String name, final int version) {
        return "{\"name\":\"" + name + "\",\"version\":" + version + ",\"children\":[]}";
    }

    /**
     * The relation of the first minute after midnight, as the topology analysis aggregates it.
     */
    private static ServiceRelationServerSideMetrics relation(final String sourceServiceId, final String destServiceId,
                                                             final int... componentIds) {
        final ServiceRelationServerSideMetrics relation = new ServiceRelationServerSideMetrics();
        relation.setSourceServiceId(sourceServiceId);
        relation.setDestServiceId(destServiceId);
        relation.setEntityId(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(sourceServiceId, destServiceId)));
        final IntList components = new IntList(componentIds.length);
        for (final int componentId : componentIds) {
            components.add(componentId);
        }
        relation.setComponentIds(components);
        relation.setTimeBucket(TimeBucket.getMinuteTimeBucket(MIDNIGHT));
        return relation;
    }

    private JDBCRuntimeRuleManagementDAO ruleDAO() {
        return new JDBCRuntimeRuleManagementDAO(support.getClient(), support.getTableHelper());
    }

    private static ContinuousProfilingPolicy policy(final String serviceId, final String configuration,
                                                    final String uuid) {
        final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
        policy.setServiceId(serviceId);
        policy.setConfigurationJson(configuration);
        policy.setUuid(uuid);
        return policy;
    }

    private static List<String> policies(final ContinuousProfilingPolicy... policies) {
        return policies(Arrays.asList(policies));
    }

    private static List<String> policies(final List<ContinuousProfilingPolicy> policies) {
        return policies.stream()
                       .map(policy -> String.join("|", policy.getServiceId(), policy.getConfigurationJson(),
                                                  policy.getUuid()))
                       .sorted()
                       .collect(Collectors.toList());
    }

    private static RuntimeRule rule(final String catalog, final String name, final String content,
                                    final String status, final long updateTime) {
        final RuntimeRule rule = new RuntimeRule();
        rule.setCatalog(catalog);
        rule.setName(name);
        rule.setContent(content);
        rule.setStatus(status);
        rule.setUpdateTime(updateTime);
        return rule;
    }

    private static List<String> rules(final List<String> kept, final RuntimeRule... rules) {
        final List<String> all = new ArrayList<>(kept);
        all.addAll(rules(rules));
        Collections.sort(all);
        return all;
    }

    private static List<String> rules(final RuntimeRule... rules) {
        return Arrays.stream(rules)
                     .map(rule -> String.join("|", rule.getCatalog(), rule.getName(), rule.getContent(),
                                              rule.getStatus(), String.valueOf(rule.getUpdateTime())))
                     .sorted()
                     .collect(Collectors.toList());
    }

    private static List<String> rules(final List<RuntimeRuleManagementDAO.RuntimeRuleFile> files) {
        return files.stream()
                    .map(file -> String.join("|", file.getCatalog(), file.getName(), file.getContent(),
                                             file.getStatus(), String.valueOf(file.getUpdateTime())))
                    .sorted()
                    .collect(Collectors.toList());
    }
}
