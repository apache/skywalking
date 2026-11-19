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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the management data OAP stores in Elasticsearch or OpenSearch through their own DAOs: UI templates the way the
 * UI management API manages them ({@link UITemplateManagementEsDAO}), continuous-profiling policies the way the policy
 * page saves them and the eBPF agents read them ({@link ContinuousProfilingPolicyEsDAO}), and runtime rules the way
 * the runtime-rule API and its reconciler keep them ({@link RuntimeRuleManagementEsDAO}). A nested class of a
 * server's IT names the server and the storage mode.
 *
 * <p>All management data shares one index, in either storage mode, so each kind is read beside rows of the others.
 * A template list is longer than a search's default page (10). Each change has its refusal: adding an id that
 * exists, changing or disabling one that does not.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ManagementDataITBase {
    /** More than a search returns without an explicit size. */
    private static final int LISTED_TEMPLATES = 12;
    private static final String CHECKOUT_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String PAYMENT_ID = IDManager.ServiceID.buildId("payment", true);

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private UITemplateManagementEsDAO templateDAO;
    private RuntimeRuleManagementEsDAO ruleDAO;
    private ContinuousProfilingPolicyEsDAO policyDAO;

    ManagementDataITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void install() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        support.installManagement(UITemplate.class);
        support.installManagement(RuntimeRule.class);
        support.installManagement(ContinuousProfilingPolicy.class);
        templateDAO = new UITemplateManagementEsDAO(support.getClient(), new UITemplate.Builder());
        ruleDAO = new RuntimeRuleManagementEsDAO(support.getClient(), new RuntimeRule.Builder());
        policyDAO = new ContinuousProfilingPolicyEsDAO(support.getClient(), new ContinuousProfilingPolicy.Builder());

        // A row of each kind beside the ones the tests read.
        assertTrue(templateDAO.addTemplate(setting("neighbour", configuration("neighbour", 1))).isStatus());
        ruleDAO.save(rule("lal", "neighbour", "rules: []", RuntimeRule.STATUS_ACTIVE, 500L));
        policyDAO.savePolicy(policy(IDManager.ServiceID.buildId("neighbour", true), "{}", "uuid-neighbour"));
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void addsATemplateOnlyWhenItsIdIsNew() throws Exception {
        final TemplateChangeStatus added = templateDAO.addTemplate(setting("added", configuration("added", 1)));
        assertEquals("added|true|null", describe(added));

        final TemplateChangeStatus again = templateDAO.addTemplate(setting("added", configuration("added", 2)));
        assertEquals("added|false|Template already exists", describe(again));
        assertEquals(template("added", configuration("added", 1), false), describe(templateDAO.getTemplate("added")));
    }

    /**
     * The template list of the UI, with and without the disabled templates, is every template whole and nothing
     * else of the index.
     */
    @Test
    public void readsEveryTemplateWithOrWithoutTheDisabledOnes() throws Exception {
        final List<String> all = new ArrayList<>();
        final List<String> enabled = new ArrayList<>();
        for (int i = 0; i < LISTED_TEMPLATES; i++) {
            final String id = String.format("listed-%02d", i);
            assertTrue(templateDAO.addTemplate(setting(id, configuration(id, 1))).isStatus());
            final boolean disabled = i % 5 == 3;
            if (disabled) {
                assertTrue(templateDAO.disableTemplate(id).isStatus());
            } else {
                enabled.add(template(id, configuration(id, 1), false));
            }
            all.add(template(id, configuration(id, 1), disabled));
        }

        final List<DashboardConfiguration> includingDisabled = templateDAO.getAllTemplates(true);
        assertEquals(all, listed(includingDisabled));
        assertEquals(enabled, listed(templateDAO.getAllTemplates(false)));
        assertTrue(includingDisabled.stream().allMatch(template -> template.getId() != null
                       && template.getConfiguration() != null),
                   "a rule or a policy read as a template");
    }

    @Test
    public void disablesAnAddedTemplateOnly() throws Exception {
        assertTrue(templateDAO.addTemplate(setting("disabled", configuration("disabled", 1))).isStatus());

        assertEquals("disabled|true|null", describe(templateDAO.disableTemplate("disabled")));
        assertEquals(template("disabled", configuration("disabled", 1), true),
                     describe(templateDAO.getTemplate("disabled")));
        assertEquals("disabled|true|null", describe(templateDAO.disableTemplate("disabled")));

        assertEquals("never-added|false|Can't find the template", describe(templateDAO.disableTemplate("never-added")));
        assertNull(templateDAO.getTemplate("never-added"));
    }

    /**
     * A change replaces the whole template, so it also enables a disabled one, the only way the API has to.
     */
    @Test
    public void changesAnAddedTemplateOnly() throws Exception {
        assertEquals("not-added|false|Can't find the template",
                     describe(templateDAO.changeTemplate(setting("not-added", configuration("not-added", 2)))));
        assertNull(templateDAO.getTemplate("not-added"));

        assertTrue(templateDAO.addTemplate(setting("changed", configuration("changed", 1))).isStatus());
        assertTrue(templateDAO.disableTemplate("changed").isStatus());
        assertEquals("changed|true|null",
                     describe(templateDAO.changeTemplate(setting("changed", configuration("changed", 2)))));
        assertEquals(template("changed", configuration("changed", 2), false),
                     describe(templateDAO.getTemplate("changed")));
    }

    /**
     * A service has one policy: saving it again, as the policy page does on every change, replaces the whole policy.
     * The eBPF agents ask for the policies of their services, some of which have none.
     */
    @Test
    public void savesAPolicyPerServiceAndReplacesItOnTheNextSave() throws Exception {
        final String withoutPolicy = IDManager.ServiceID.buildId("policy-less", true);
        final ContinuousProfilingPolicy checkout = policy(CHECKOUT_ID, "{\"targets\":[\"ON_CPU\"]}", "uuid-1");
        final ContinuousProfilingPolicy payment = policy(PAYMENT_ID, "{\"targets\":[\"OFF_CPU\"]}", "uuid-2");
        policyDAO.savePolicy(checkout);
        policyDAO.savePolicy(payment);
        assertEquals(policies(checkout), policies(policyDAO.queryPolicies(Collections.singletonList(CHECKOUT_ID))));

        final ContinuousProfilingPolicy changed = policy(CHECKOUT_ID, "{\"targets\":[\"NETWORK\"]}", "uuid-3");
        policyDAO.savePolicy(changed);
        assertEquals(policies(changed, payment),
                     policies(policyDAO.queryPolicies(Arrays.asList(CHECKOUT_ID, PAYMENT_ID, withoutPolicy))));
        assertEquals(policies(payment), policies(policyDAO.queryPolicies(Collections.singletonList(PAYMENT_ID))));
        assertEquals(Collections.emptyList(), policyDAO.queryPolicies(Collections.singletonList(withoutPolicy)));
    }

    /**
     * Runtime rules are kept by catalog and name, the same name in two catalogs being two rules. A save replaces the
     * whole rule, as {@code /addOrUpdate} and {@code /inactivate} do, and a delete removes the rule of one catalog.
     * The reconciler reads every rule, so each read is compared whole, with the rules already kept.
     */
    @Test
    public void savesReplacesAndDeletesRulesByCatalogAndName() throws Exception {
        final List<String> kept = rules(ruleDAO.getAll());
        final String active = RuntimeRule.STATUS_ACTIVE;
        final RuntimeRule vm = rule("otel-rules", "vm", "metricPrefix: vm", active, 1_000L);
        final RuntimeRule logVm = rule("log-mal-rules", "vm", "metricPrefix: log_vm", active, 1_001L);
        final RuntimeRule mysql = rule("otel-rules", "mysql", "metricPrefix: mysql", active, 1_002L);
        for (final RuntimeRule rule : Arrays.asList(vm, logVm, mysql)) {
            ruleDAO.save(rule);
        }
        assertEquals(rules(kept, vm, logVm, mysql), rules(ruleDAO.getAll()));

        final RuntimeRule inactiveVm =
            rule("otel-rules", "vm", "metricPrefix: vm_v2", RuntimeRule.STATUS_INACTIVE, 2_000L);
        ruleDAO.save(inactiveVm);
        assertEquals(rules(kept, inactiveVm, logVm, mysql), rules(ruleDAO.getAll()));

        ruleDAO.delete("log-mal-rules", "vm");
        assertEquals(rules(kept, inactiveVm, mysql), rules(ruleDAO.getAll()));
        ruleDAO.delete("otel-rules", "mysql");
        assertEquals(rules(kept, inactiveVm), rules(ruleDAO.getAll()));
    }

    /**
     * A rule's name is its path under the catalog, so it may hold a '/'.
     */
    @Test
    public void savesAndDeletesARuleNamedByAPath() throws Exception {
        final List<String> kept = rules(ruleDAO.getAll());
        final RuntimeRule gateway = rule("otel-rules", "aws-gateway/gateway-service", "metricPrefix: aws_gateway",
                                         RuntimeRule.STATUS_ACTIVE, 3_000L);
        ruleDAO.save(gateway);
        assertEquals(rules(kept, gateway), rules(ruleDAO.getAll()));

        ruleDAO.delete("otel-rules", "aws-gateway/gateway-service");
        assertEquals(kept, rules(ruleDAO.getAll()));
    }

    private static List<String> listed(final List<DashboardConfiguration> templates) {
        return templates.stream()
                        .filter(template -> template.getId() != null && template.getId().startsWith("listed-"))
                        .map(ManagementDataITBase::describe)
                        .sorted()
                        .collect(Collectors.toList());
    }

    private static String describe(final DashboardConfiguration template) {
        return template(template.getId(), template.getConfiguration(), template.isDisabled());
    }

    private static String template(final String id, final String configuration, final boolean disabled) {
        return id + "|" + configuration + "|" + disabled;
    }

    private static String describe(final TemplateChangeStatus status) {
        return status.getId() + "|" + status.isStatus() + "|" + status.getMessage();
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
