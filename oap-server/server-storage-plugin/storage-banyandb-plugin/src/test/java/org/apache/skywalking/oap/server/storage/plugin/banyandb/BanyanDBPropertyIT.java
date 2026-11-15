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

import java.io.IOException;
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
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBContinuousProfilingPolicyDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes and reads the management data OAP keeps as BanyanDB properties — UI templates, runtime rules and continuous
 * profiling policies — through their own DAOs: every object of a list longer than BanyanDB's default limit, a lookup
 * by id, a missing id, an update that replaces the object, and the states each kind has (a disabled template, a
 * deleted rule).
 */
@Testcontainers
public class BanyanDBPropertyIT {
    /** More templates and rules than BanyanDB returns without an explicit limit (100). */
    private static final int OBJECTS = 120;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBUITemplateManagementDAO TEMPLATES;
    private static BanyanDBRuntimeRuleManagementDAO RULES;
    private static BanyanDBContinuousProfilingPolicyDAO POLICIES;

    @BeforeAll
    public static void install() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        SUPPORT.installManagement(UITemplate.class, DefaultScopeDefine.UI_TEMPLATE, UITemplate.INDEX_NAME);
        SUPPORT.installManagement(RuntimeRule.class, DefaultScopeDefine.RUNTIME_RULE, RuntimeRule.INDEX_NAME);
        SUPPORT.installManagement(ContinuousProfilingPolicy.class, DefaultScopeDefine.CONTINUOUS_PROFILING_POLICY,
                                  ContinuousProfilingPolicy.INDEX_NAME);
        TEMPLATES = new BanyanDBUITemplateManagementDAO(SUPPORT.getClient());
        RULES = new BanyanDBRuntimeRuleManagementDAO(SUPPORT.getClient());
        POLICIES = new BanyanDBContinuousProfilingPolicyDAO(SUPPORT.getClient());
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void keepsUITemplates() throws IOException {
        final List<String> all = new ArrayList<>();
        for (int i = 0; i < OBJECTS; i++) {
            assertTrue(TEMPLATES.addTemplate(setting(templateId(i), "{\"v\":" + i + "}")).isStatus());
            all.add(templateId(i) + "|{\"v\":" + i + "}|false");
        }
        Collections.sort(all);
        assertEquals(all, templates(TEMPLATES.getAllTemplates(false)));
        assertEquals(templateId(7) + "|{\"v\":7}|false", template(TEMPLATES.getTemplate(templateId(7))));
        assertNull(TEMPLATES.getTemplate("template-missing"));

        assertTrue(TEMPLATES.disableTemplate(templateId(7)).isStatus());
        assertEquals(templateId(7) + "|{\"v\":7}|true", template(TEMPLATES.getTemplate(templateId(7))));
        assertEquals(OBJECTS - 1, TEMPLATES.getAllTemplates(false).size());
        assertEquals(OBJECTS, TEMPLATES.getAllTemplates(true).size());

        // A change replaces the whole template, so it also re-enables a disabled one.
        assertTrue(TEMPLATES.changeTemplate(setting(templateId(7), "{\"v\":\"changed\"}")).isStatus());
        assertEquals(templateId(7) + "|{\"v\":\"changed\"}|false", template(TEMPLATES.getTemplate(templateId(7))));
        assertEquals(OBJECTS, TEMPLATES.getAllTemplates(false).size());
    }

    @Test
    public void keepsRuntimeRules() throws IOException {
        final List<String> all = new ArrayList<>();
        for (int i = 0; i < OBJECTS; i++) {
            // The same rule name in two catalogs is two rules.
            final String catalog = i % 2 == 0 ? "otel-rules" : "log-mal-rules";
            final String name = "rule-" + (i / 2);
            RULES.save(rule(catalog, name, "content " + i, "ACTIVE", 1000L + i));
            all.add(catalog + "|" + name + "|content " + i + "|ACTIVE|" + (1000L + i));
        }
        Collections.sort(all);
        assertEquals(all, rules(RULES.getAll()));

        RULES.save(rule("otel-rules", "rule-0", "content changed", "INACTIVE", 5000L));
        RULES.delete("log-mal-rules", "rule-0");
        final List<String> read = rules(RULES.getAll());
        assertEquals(OBJECTS - 1, read.size());
        assertTrue(read.contains("otel-rules|rule-0|content changed|INACTIVE|5000"));
        assertTrue(read.stream().noneMatch(r -> r.startsWith("log-mal-rules|rule-0|")));
    }

    @Test
    public void keepsContinuousProfilingPolicies() throws IOException {
        final String first = IDManager.ServiceID.buildId("policy-it-0", true);
        final String second = IDManager.ServiceID.buildId("policy-it-1", true);
        POLICIES.savePolicy(policy(first, "{\"targets\":[\"ON_CPU\"]}", "uuid-0"));
        POLICIES.savePolicy(policy(second, "{\"targets\":[\"OFF_CPU\"]}", "uuid-1"));
        POLICIES.savePolicy(policy(first, "{\"targets\":[\"NETWORK\"]}", "uuid-2"));
        assertEquals(Arrays.asList(first + "|{\"targets\":[\"NETWORK\"]}|uuid-2", second + "|{\"targets\":[\"OFF_CPU\"]}|uuid-1"),
                     POLICIES.queryPolicies(Arrays.asList(first, second, IDManager.ServiceID.buildId("policy-missing", true)))
                             .stream().map(p -> p.getServiceId() + "|" + p.getConfigurationJson() + "|" + p.getUuid())
                             .sorted().collect(Collectors.toList()));
    }

    private static String templateId(int i) {
        return String.format("template-%03d", i);
    }

    private static DashboardSetting setting(String id, String configuration) {
        final DashboardSetting setting = new DashboardSetting();
        setting.setId(id);
        setting.setConfiguration(configuration);
        return setting;
    }

    private static List<String> templates(List<DashboardConfiguration> templates) {
        return templates.stream().map(BanyanDBPropertyIT::template).sorted().collect(Collectors.toList());
    }

    private static String template(DashboardConfiguration template) {
        return template.getId() + "|" + template.getConfiguration() + "|" + template.isDisabled();
    }

    private static RuntimeRule rule(String catalog, String name, String content, String status, long updateTime) {
        final RuntimeRule rule = new RuntimeRule();
        rule.setCatalog(catalog);
        rule.setName(name);
        rule.setContent(content);
        rule.setStatus(status);
        rule.setUpdateTime(updateTime);
        return rule;
    }

    private static List<String> rules(List<RuntimeRuleManagementDAO.RuntimeRuleFile> rules) {
        return rules.stream()
                    .map(r -> String.join("|", r.getCatalog(), r.getName(), r.getContent(), r.getStatus(),
                                          String.valueOf(r.getUpdateTime())))
                    .sorted().collect(Collectors.toList());
    }

    private static ContinuousProfilingPolicy policy(String serviceId, String configuration, String uuid) {
        final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
        policy.setServiceId(serviceId);
        policy.setConfigurationJson(configuration);
        policy.setUuid(uuid);
        return policy;
    }
}
