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

public class ConfigHelper {
    private static Logger logger = LogManager.getLogger(ConfigHelper.class);

    private ConfigHelper() {
    }

    public static String testDate() {
        return System.getProperty("testDate");
    }

    public static String testCases() {
        String testCasesInput = System.getProperty("testCases", "");
        if (testCasesInput.length() > 0) {
            return testCasesInput;
        }
        String testCasePath = System.getProperty("testCasePath", "");

        if (testCasePath.length() == 0) {
            logger.warn("test case dir is empty");
            return "";
        }

        File testCaseDir = new File(testCasePath);
        if (!testCaseDir.exists() || !testCaseDir.isDirectory()) {
            logger.warn("test case dir is not exists or is not directory");
            return "";
        }

        StringBuilder testCases = new StringBuilder();
        File[] testCasesDir = testCaseDir.listFiles();
        for (File file : testCasesDir) {
            testCases.append(file.getName() + ",");
        }

        return testCases.toString();
    }

    public static String testCaseBaseDir() {
        return System.getProperty("testCasePath", "");
    }

    public static boolean isV2() {
        return Boolean.getBoolean("v2");
    }

    public static String reportFilePath() {
        return System.getProperty("reportFilePath");
    }

    public static String agentBranch() {
        return System.getProperty("agentBranch");
    }

    public static String agentCommit() {
        return System.getProperty("agentCommitId");
    }

    public static String casesBranch() {
        return System.getProperty("casesBranch", "").replace("/", "-");
    }

    public static String caseCommitId() {
        return System.getProperty("casesCommitId", "");
    }

    public static String committer() {
        return System.getProperty("committer", "");
    }
}
