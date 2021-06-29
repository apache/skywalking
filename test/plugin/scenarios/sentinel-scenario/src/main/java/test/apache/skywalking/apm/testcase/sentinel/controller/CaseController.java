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

package test.apache.skywalking.apm.testcase.sentinel.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    @PostConstruct
    public void setUp() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule("test_SphO_entry");
        // set limit qps to 0
        rule.setCount(0);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setLimitApp("default");
        rules.add(rule);

        FlowRule rule2 = new FlowRule("test_SphU_asyncEntry");
        // set limit qps to 0
        rule2.setCount(0);
        rule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule2.setLimitApp("default");
        rules.add(rule2);
        FlowRuleManager.loadRules(rules);
    }

    @RequestMapping("/sentinel-scenario")
    @ResponseBody
    public String testcase() throws InterruptedException, ExecutionException {
        Entry entry = null;
        try {
            entry = SphU.entry("test_SphU_entry");
            if (SphO.entry("test_SphO_entry")) {
                Thread.sleep(1000L);
                SphO.exit();
            }
            Thread.sleep(1000L);
        } catch (BlockException ex) {

        } catch (Exception ex) {
            Tracer.traceEntry(ex, entry);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }

        try {
            AsyncEntry asyncEntry = SphU.asyncEntry("test_SphU_asyncEntry");
            new Thread(() -> {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    asyncEntry.exit();
                }
            }).start();
        } catch (BlockException ex) {
        }
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }

}
