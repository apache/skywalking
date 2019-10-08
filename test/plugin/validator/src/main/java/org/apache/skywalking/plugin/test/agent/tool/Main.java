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
 */

package org.apache.skywalking.plugin.test.agent.tool;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.DataAssert;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Data;
import org.apache.skywalking.plugin.test.agent.tool.report.entity.Report;
import org.apache.skywalking.plugin.test.agent.tool.report.entity.TestCase;
import org.apache.skywalking.plugin.test.agent.tool.report.entity.TestCaseDesc;
import org.apache.skywalking.plugin.test.agent.tool.validator.exception.AssertFailedException;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        Report report = new Report();
        String testCasePath = ConfigHelper.testCaseBaseDir();

        boolean success = false;
        if (ConfigHelper.isV2()) {
            success |= verify(new File(testCasePath), TestCaseDesc.ParserV2.parse(), report).isSuccess();
        } else {
            String[] testCases = ConfigHelper.testCases().split(",");

            for (String aCase : testCases) {
                File casePath = new File(testCasePath, aCase);
                File descFile = new File(casePath, "testcase.desc");
                success |= verify(casePath, TestCaseDesc.Parser.parse(descFile), report).isSuccess();
            }
        }

        if (!ConfigHelper.isV2()) {
            try {
                report.generateReport(ConfigHelper.reportFilePath());
            } catch (Exception e) {
                logger.error("Failed to generate report file", e);
            }
        }
        System.exit(success ? 0 : 1);
    }

    static TestCase verify(File casePath, TestCaseDesc testCaseDesc, Report report) {
        TestCase testCase = new TestCase(testCaseDesc.getTestComponents());
        try {
            logger.info("start to assert data of test case[{}]", testCase.getCaseName());
            File actualData = new File(casePath, "actualData.yaml");
            File expectedData = new File(casePath, "expectedData.yaml");

            if (actualData.exists() && expectedData.exists()) {
                try {
                    DataAssert.assertEquals(Data.Loader.loadData("expectedData.yaml", expectedData),
                            Data.Loader.loadData("actualData.yaml", actualData));
                    testCase.testedSuccessfully();
                } catch (AssertFailedException e) {
                    logger.error("\nassert failed.\n{}\n", e.getCauseMessage());
                }
            } else {
                logger.error("assert failed. because actual data {} and expected data {}", actualData.exists() ? "founded" : "not founded", actualData.exists() ? "founded" : "not founded");
            }
        } catch (Exception e) {
            logger.error("assert test case {} failed.", testCase.getCaseName(), e);
        }
        report.addTestCase(testCaseDesc.getProjectName(), testCaseDesc.getTestFramework(), testCase);
        return testCase;
    }

}
