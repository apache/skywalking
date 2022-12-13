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

package org.apache.skywalking.oap.meter.analyzer;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MetricConvertTest {

    @Test
    public void testOneLevelExp() {
        MockMetricRuleConfig mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            "tag({tags -> tags.service_name = 2})",
            "tag({tags -> tags.service_name = 1})",
            "{ tags -> tags.job_name == 'apisix-monitoring' }",
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections"
            )),
            null
        );
        MockMetricConvert metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals("filter", "{ tags -> tags.job_name == 'apisix-monitoring' }", metricConvert.filter);
        Assert.assertEquals(
            "exp",
            "((apisix_nginx_http_current_connections.tag({tags -> tags.service_name = 1}))).tag({tags -> tags.service_name = 2})",
            metricConvert.exp
        );

        // expSuffix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            null,
            "tag({tags -> tags.service_name = 1})",
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "(apisix_nginx_http_current_connections.tag({tags -> tags.service_name = 1}))",
            metricConvert.exp
        );

        // expPrefix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            "tag({tags -> tags.service_name = 2})",
            null,
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "(apisix_nginx_http_current_connections).tag({tags -> tags.service_name = 2})",
            metricConvert.exp
        );

        // expPrefix and expSuffix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            null,
            null,
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "apisix_nginx_http_current_connections",
            metricConvert.exp
        );

    }

    @Test
    public void testMultipleLevelExp() {
        MockMetricRuleConfig mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            "tag({tags -> tags.service_name = 2})",
            "tag({tags -> tags.service_name = 1})",
            "{ tags -> tags.job_name == 'apisix-monitoring' }",
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections.sum(['a'])"
            )),
            null
        );
        MockMetricConvert metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals("filter", "{ tags -> tags.job_name == 'apisix-monitoring' }", metricConvert.filter);
        Assert.assertEquals(
            "exp",
            "(((apisix_nginx_http_current_connections.tag({tags -> tags.service_name = 1})).sum(['a']))).tag({tags -> tags.service_name = 2})",
            metricConvert.exp
        );

        // expSuffix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            null,
            "tag({tags -> tags.service_name = 1})",
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections.downsampling(LATEST)"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "((apisix_nginx_http_current_connections.tag({tags -> tags.service_name = 1})).downsampling(LATEST))",
            metricConvert.exp
        );

        // expPrefix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            "tag({tags -> tags.service_name = 2})",
            null,
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections.downsampling(LATEST)"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "(apisix_nginx_http_current_connections.downsampling(LATEST)).tag({tags -> tags.service_name = 2})",
            metricConvert.exp
        );

        // expPrefix and expSuffix is null
        mockMetricRuleConfig = new MockMetricRuleConfig(
            "meter_apisix",
            null,
            null,
            null,
            Arrays.asList(new MockRule(
                "sv_http_connections",
                "apisix_nginx_http_current_connections"
            )),
            null
        );
        metricConvert = new MockMetricConvert(mockMetricRuleConfig, null);
        Assert.assertEquals("metrics name", "meter_apisix_sv_http_connections", metricConvert.metricsName);
        Assert.assertEquals(
            "exp", "apisix_nginx_http_current_connections",
            metricConvert.exp
        );

    }

    static class MockMetricConvert extends MetricConvert {
        private String metricsName;
        private String filter;
        private String exp;

        public MockMetricConvert(final MetricRuleConfig rule, final MeterSystem service) {
            super(rule, service);
        }

        @Override
        Analyzer buildAnalyzer(final String metricsName,
                               final String filter,
                               final String exp,
                               final MeterSystem service) {
            this.metricsName = metricsName;
            this.filter = filter;
            this.exp = exp;
            return null;
        }

    }

    @Getter
    @AllArgsConstructor
    static class MockMetricRuleConfig implements MetricRuleConfig {
        private String metricPrefix;
        private String expSuffix;
        private String expPrefix;
        private String filter;
        private List<MockRule> metricsRules;
        private String initExp;

    }

    @Getter
    @AllArgsConstructor
    public static class MockRule implements MetricRuleConfig.RuleConfig {
        private String name;
        private String exp;
    }
}
