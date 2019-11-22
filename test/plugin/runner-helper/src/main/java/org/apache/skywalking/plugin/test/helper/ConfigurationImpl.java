/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.helper;

import com.google.common.base.Strings;
import org.apache.skywalking.plugin.test.helper.exception.ConfigureFileNotFoundException;
import org.apache.skywalking.plugin.test.helper.util.StringUtils;
import org.apache.skywalking.plugin.test.helper.vo.CaseConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class ConfigurationImpl implements IConfiguration {
    private CaseConfiguration configuration;
    private final String scenarioHome;

    public ConfigurationImpl() throws FileNotFoundException, ConfigureFileNotFoundException {
        String configureFile = System.getProperty("configure.file");
        if (StringUtils.isBlank(configureFile)) {
            throw new ConfigureFileNotFoundException();
        }

        this.configuration = new Yaml().loadAs(new FileReader(new File(configureFile)), CaseConfiguration.class);
        this.scenarioHome = System.getProperty("scenario.home");
        if (!Strings.isNullOrEmpty(this.configuration.getRunningMode())) {
            String runningMode = this.configuration.getRunningMode();
            if (!runningMode.matches("default|with_optional|with_bootstrap")) {
                throw new RuntimeException("RunningMode (" + runningMode + ") is not defined.");
            }
        }
    }

    @Override
    public String agentHome() {
        return System.getProperty("agent.dir");
    }

    @Override public RunningType runningType() {
        return (configuration.getDependencies() != null && configuration.getDependencies().size() > 0) ?
            RunningType.DockerCompose :
            RunningType.Container;
    }

    @Override public ScenarioRunningScriptGenerator scenarioGenerator() {
        switch (runningType()) {
            case DockerCompose:
                return new DockerComposeRunningGenerator();
            case Container:
                return new DockerContainerRunningGenerator();
            default:
                throw new RuntimeException();
        }
    }

    @Override public CaseConfiguration caseConfiguration() {
        return this.configuration;
    }

    @Override public String scenarioName() {
        return System.getProperty("scenario.name");
    }

    @Override public String scenarioVersion() {
        return System.getProperty("scenario.version");
    }

    @Override public String testFramework() {
        return this.configuration.getFramework();
    }

    @Override public String entryService() {
        return this.configuration.getEntryService();
    }

    @Override public String healthCheck() {
        return this.configuration.getHealthCheck();
    }

    @Override
    public String startScript() {
        return this.configuration.getStartScript();
    }

    @Override public String dockerImageName() {
        switch (this.configuration.getType().toLowerCase()) {
        case "tomcat" :
            return "skywalking/agent-test-tomcat";
        case "jvm" :
            return "skywalking/agent-test-jvm";
        }

        throw new RuntimeException("Illegal type!");
    }

    @Override
    public String dockerImageVersion() {
        return System.getProperty("docker.image.version", "latest");
    }

    @Override
    public String dockerNetworkName() {
        return (scenarioName() + "-" + dockerImageVersion()).toLowerCase();
    }

    @Override
    public String dockerContainerName() {
        return (scenarioName() + "-" + scenarioVersion() + "-" + dockerImageVersion()).toLowerCase();
    }

    @Override public String scenarioHome() {
        return this.scenarioHome;
    }

    @Override public String outputDir(){
        return System.getProperty("output.dir");
    }

}
