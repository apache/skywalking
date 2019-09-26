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