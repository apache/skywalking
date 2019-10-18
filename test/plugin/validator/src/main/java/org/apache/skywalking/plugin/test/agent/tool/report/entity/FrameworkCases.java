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

import java.util.ArrayList;
import java.util.List;

public class FrameworkCases {
    private String testFramework;
    private int successCaseCount;
    private int totalCaseCount;
    private List<CaseVersionCategory> caseByVersionCategories;

    public FrameworkCases(String testFramework) {
        this.testFramework = testFramework;
        this.caseByVersionCategories = new ArrayList<>();
    }

    public void addTestCase(String versionCategory, TestCase aCase) {
        CaseVersionCategory caseVersionCategory = findCategoryForProject(versionCategory);
        if (caseVersionCategory == null) {
            caseVersionCategory = new CaseVersionCategory(versionCategory);
            this.caseByVersionCategories.add(caseVersionCategory);
        }

        caseVersionCategory.addTestCases(aCase);

        if (aCase.isSuccessfully()) {
            successCaseCount++;
        }
        totalCaseCount++;
    }

    private CaseVersionCategory findCategoryForProject(String projectName) {
        for (CaseVersionCategory project : caseByVersionCategories) {
            if (project.getName().equals(projectName)) {
                return project;
            }
        }

        return null;
    }

    public String getTestFramework() {
        return testFramework;
    }

    public List<CaseVersionCategory> getCaseByVersionCategories() {
        return caseByVersionCategories;
    }

    public int getSuccessCaseCount() {
        return successCaseCount;
    }

    public int getTotalCaseCount() {
        return totalCaseCount;
    }
}
