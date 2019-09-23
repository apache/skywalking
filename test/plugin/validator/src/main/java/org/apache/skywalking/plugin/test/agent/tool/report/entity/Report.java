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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.ConfigHelper;

public class Report {
    private static Logger logger = LogManager.getLogger(Report.class);
    private final String testDate;
    private final String testBranch;
    private final String commitId;
    private final String caseBranch;
    private final String caseCommit;
    private final String committer;
    private int successCaseCount;
    private int totalCaseCount;
    private List<FrameworkCases> frameworkCases;

    public Report() {
        this.testDate = formatTestDate(ConfigHelper.testDate());
        this.testBranch = ConfigHelper.agentBranch();
        this.commitId = ConfigHelper.agentCommit();
        this.caseBranch = ConfigHelper.casesBranch();
        this.caseCommit = ConfigHelper.caseCommitId();
        this.committer = ConfigHelper.committer();
        this.frameworkCases = new ArrayList<>();
    }

    public String getTestDate() {
        return testDate;
    }

    public String getTestBranch() {
        return testBranch;
    }

    public String getCommitId() {
        return commitId;
    }

    public List<FrameworkCases> getFrameworkCases() {
        return frameworkCases;
    }

    private FrameworkCases findCasesByFrameworkName(String testFramework) {
        for (FrameworkCases ascenario : frameworkCases) {
            if (ascenario.getTestFramework().equalsIgnoreCase(testFramework)) {
                return ascenario;
            }
        }

        return null;
    }

    private String formatTestDate(String date) {
        try {
            Date testDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm").parse(date);
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(testDate);
        } catch (ParseException e) {
            logger.error("Failed to format date[{}].", date, e);
        }
        return date;
    }

    public void generateReport(String path) throws IOException, TemplateException, ParseException {
        File reportFile = generateReportFile(path);

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);
        cfg.setClassForTemplateLoading(this.getClass(), "/template");
        cfg.setDefaultEncoding("UTF-8");
        Template template = cfg.getTemplate("main-report.md.ftl");
        StringWriter stringWriter = new StringWriter();
        Map<String, Report> resultMap = new HashMap<>();
        resultMap.put("testReport", this);
        template.process(resultMap, stringWriter);
        FileWriter fileOutputStream = new FileWriter(reportFile, false);
        fileOutputStream.write(stringWriter.getBuffer().toString());
        stringWriter.close();
        fileOutputStream.close();
    }

    /**
     * File Path Year - Month - apache - testReport-2019-10-10-10-11-11.md - testReport-2019-10-10-10-11-12.md -
     * ascrutae ...
     *
     * @return
     */
    private File generateReportFile(String basePath) throws ParseException, IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(testDate));
        File testReportDir = new File(new File(new File(basePath, calendar.get(Calendar.YEAR) + ""), (calendar.get(Calendar.MONTH) + 1) + ""), ConfigHelper.committer());
        if (!testReportDir.exists()) {
            testReportDir.mkdirs();
        }
        String fileName = "testReport-" + ConfigHelper.casesBranch() + "-" + ConfigHelper.testDate() + ".md";
        File testReportFile = new File(testReportDir, fileName);
        if (!testReportFile.exists()) {
            testReportFile.createNewFile();
        }
        return testReportFile;
    }

    public void addTestCase(String projectName, String framework, TestCase aCase) {
        FrameworkCases frameworkCases = findCasesByFrameworkName(framework);
        if (frameworkCases == null) {
            frameworkCases = new FrameworkCases(framework);
            this.frameworkCases.add(frameworkCases);
        }

        frameworkCases.addTestCase(projectName, aCase);

        if (aCase.isSuccessfully()) {
            successCaseCount++;
        }
        totalCaseCount++;
    }

    public int getSuccessCaseCount() {
        return successCaseCount;
    }

    public int getTotalCaseCount() {
        return totalCaseCount;
    }

    public String getCaseBranch() {
        return caseBranch;
    }

    public String getCaseCommit() {
        return caseCommit;
    }
}
