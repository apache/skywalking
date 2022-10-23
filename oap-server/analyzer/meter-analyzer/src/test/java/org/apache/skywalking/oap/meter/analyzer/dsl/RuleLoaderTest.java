package org.apache.skywalking.oap.meter.analyzer.dsl;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class RuleLoaderTest {
    @Parameterized.Parameter
    public List<String> enabledRule;

    @Parameterized.Parameter(1)
    public int rulesNumber;

    @Parameterized.Parameter(2)
    public boolean isThrow;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            // abc/abc.yml is a not well-formed yml file.
            {Arrays.asList("abc/*"), 0, true},
            {Arrays.asList("k8s/*"), 4, false},
            {Arrays.asList("/k8s/*.yaml"), 4, false},
            {Arrays.asList("/k8s/*.yml"), 4, false},
            {Arrays.asList("k8s/*.yaml"), 4, false},
            {Arrays.asList("k8s/*.yml"), 4, false},
            {Arrays.asList("k8s/k8s-cluster.yml"), 1, false},
            {Arrays.asList("/k8s/k8s-cluster.yml"), 1, false},
            {Arrays.asList("/k8s/k8s-cluster.yaml"), 1, false},
            {Arrays.asList("k8s/k8s-cluster.yaml"), 1, false},
            {Arrays.asList("k8s/k8s-cluster"), 1, false},
            {Arrays.asList("oap.yaml"), 1, false},
            {Arrays.asList("oap.yml"), 1, false},
            {Arrays.asList("oap"), 1, false},
            {Arrays.asList("/oap.yaml"), 1, false},
            {Arrays.asList("/oap.yml"), 1, false},
            {Arrays.asList("/oap.yml", "/k8s/*"), 5, false},
            //if enabledRule not found, will not fail but will not load any rules
            {Arrays.asList(UUID.randomUUID().toString()), 0, false},
        });
    }

    @Test
    public void test() {
        List<Rule> rules = null;
        try {
            rules = Rules.loadRules("otel-rules", enabledRule);
        } catch (Exception e) {
            if (isThrow) {
                return;
            }
            fail("load rules failed");
        }

        assertThat(rules.size(), is(rulesNumber));
    }
}
