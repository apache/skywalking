# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Test Report - ${testReport.testDate}

- tests  : **${testReport.successCaseCount} passed**. **${testReport.totalCaseCount - testReport.successCaseCount} failed**
- branch name : **[${testReport.testBranch}](https://github.com/apache/incubator-skywalking/tree/${testReport.testBranch})**
- commit id : **[${testReport.commitId}](https://github.com/apache/incubator-skywalking/commit/${testReport.commitId})**
- cases branch: **[${testReport.caseBranch}](https://github.com/SkywalkingTest/skywalking-autotest-scenarios/tree/${testReport.caseBranch})**
- cases commit id: **[${testReport.caseCommit}](https://github.com/SkywalkingTest/skywalking-autotest-scenarios/commit/${testReport.caseCommit})**

## Cases List

| Case description | Status | Cases|
|:-----|:-----:|:-----:|
<#list testReport.frameworkCases as frameworkCases>
|${frameworkCases.testFramework}| **${frameworkCases.successCaseCount} passed. ${frameworkCases.totalCaseCount - frameworkCases.successCaseCount} failed**| [click me](#${frameworkCases.testFramework?lower_case}) |
</#list>

<#list testReport.frameworkCases as frameworkCases>
## ${frameworkCases.testFramework}

### <#list frameworkCases.caseByVersionCategories as versionCategory>
|  Version     | Passed | Failed|
|:------------- |:-------:|:-----:|
<#list versionCategory.testCases as item>
| ${item.caseName}  | <#if item.success>:heavy_check_mark:</#if>|<#if !item.success>:heavy_check_mark:</#if>|
</#list>
</#list>

</#list>
