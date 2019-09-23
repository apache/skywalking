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

package org.apache.skywalking.plugin.test.agent.tool.report.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestCaseDesc {
    private static Logger logger = LogManager.getLogger(TestCaseDesc.class);
    private String testFramework;
    private String testComponents;
    private String projectName;

    private TestCaseDesc() {
    }

    public static class Parser {
        public static TestCaseDesc parse(File caseDescFile) {
            TestCaseDesc testCase = new TestCaseDesc();
            Properties properties = new Properties();
            if (caseDescFile.exists()) {
                try {
                    properties.load(new FileInputStream(caseDescFile));
                } catch (IOException e) {
                    logger.error("Failed to parse case desc file[{}]", caseDescFile.getAbsoluteFile());
                    return testCase;
                }
            }

            testCase.projectName = properties.getProperty("case.projectName", null);
            testCase.testFramework = properties.getProperty("case.testFramework", null);
            testCase.testComponents = properties.getProperty("case.testComponents", null);
            logger.info("load case desc: projectName[{}] testFramework[{}], testComponents[{}], cooperativeFrameworks[{}]",
                testCase.projectName,
                testCase.testFramework,
                testCase.testComponents);
            return testCase;
        }
    }

    public static class ParserV2 {
        public static TestCaseDesc parse() {
            TestCaseDesc testCase = new TestCaseDesc();
            testCase.projectName = System.getProperty("case.projectName", null);
            testCase.testFramework = System.getProperty("case.testFramework", null);
            testCase.testComponents = System.getProperty("case.testComponents", null);
            logger.info("load case desc: projectName[{}] testFramework[{}], testComponents[{}], cooperativeFrameworks[{}]",
                    testCase.projectName,
                    testCase.testFramework,
                    testCase.testComponents);
            return testCase;
        }
    }

    public String getTestFramework() {
        return testFramework;
    }

    public String getTestComponents() {
        return testComponents;
    }

    public String getProjectName() {
        return projectName;
    }
}
