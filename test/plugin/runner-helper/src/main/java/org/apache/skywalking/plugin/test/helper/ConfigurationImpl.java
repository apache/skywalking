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
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.plugin.test.helper.exception.ConfigureFileNotFoundException;
import org.apache.skywalking.plugin.test.helper.vo.CaseConfiguration;
import org.apache.skywalking.plugin.test.helper.vo.DependencyComponent;
import org.apache.skywalking.plugin.test.helper.vo.DockerService;
import org.apache.skywalking.plugin.test.helper.vo.RequestHeader;
import org.yaml.snakeyaml.Yaml;

public class ConfigurationImpl implements IConfiguration {
    private final CaseConfiguration configuration;
    private final String scenarioHome;

    public ConfigurationImpl() throws FileNotFoundException, ConfigureFileNotFoundException {
        String configureFile = System.getProperty("configure.file");
        if (Strings.isNullOrEmpty(configureFile)) {
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

    @Override
    public RunningType runningType() {
        return (configuration.getDependencies() != null && configuration.getDependencies()
                                                                        .size() > 0) ? RunningType.DockerCompose : RunningType.Container;
    }

    @Override
    public ScenarioRunningScriptGenerator scenarioGenerator() {
        switch (runningType()) {
            case DockerCompose:
                return new DockerComposeRunningGenerator();
            case Container:
                return new DockerContainerRunningGenerator();
            default:
                throw new RuntimeException();
        }
    }

    @Override
    public CaseConfiguration caseConfiguration() {
        return this.configuration;
    }

    @Override
    public String scenarioName() {
        return System.getProperty("scenario.name");
    }

    @Override
    public String scenarioVersion() {
        return System.getProperty("scenario.version");
    }

    @Override
    public String entryService() {
        return this.configuration.getEntryService();
    }

    @Override
    public String healthCheck() {
        return this.configuration.getHealthCheck();
    }

    @Override
    public String startScript() {
        return this.configuration.getStartScript();
    }

    @Override
    public String catalinaOpts() {
        List<String> environment = this.configuration.getEnvironment() != null ? this.configuration.getEnvironment() : Collections
            .emptyList();
        return environment.stream()
                          .filter(it -> it.startsWith("CATALINA_OPTS="))
                          .findFirst()
                          .orElse("")
                          .replaceAll("^CATALINA_OPTS=", "");
    }

    @Override
    public String dockerImageName() {
        switch (this.configuration.getType().toLowerCase()) {
            case "tomcat":
                return "skywalking/agent-test-tomcat";
            case "jvm":
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

    @Override
    public String scenarioHome() {
        return this.scenarioHome;
    }

    @Override
    public String outputDir() {
        return System.getProperty("output.dir");
    }

    @Override
    public String jacocoHome() {
        return System.getProperty("jacoco.home");
    }

    @Override
    public String debugMode() {
        return System.getProperty("debug.mode");
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> root = new HashMap<>();

        root.put("agent_home", agentHome());
        root.put("scenario_home", scenarioHome());
        root.put("scenario_name", scenarioName());
        root.put("scenario_version", scenarioVersion());
        root.put("health_check", healthCheck());
        root.put("start_script", startScript());
        root.put("catalina_opts", catalinaOpts());
        root.put("entry_service", entryService());
        root.put("docker_image_name", dockerImageName());
        root.put("docker_image_version", dockerImageVersion());
        root.put("docker_container_name", dockerContainerName());
        root.put("jacoco_home", jacocoHome());
        root.put("debug_mode", debugMode());

        root.put("expose", caseConfiguration().getExpose());
        root.put("hostname", caseConfiguration().getHostname());
        root.put("depends_on", caseConfiguration().getDependsOn());
        root.put("environments", caseConfiguration().getEnvironment());

        root.put("network_name", dockerNetworkName());
        root.put("services", convertDockerServices(scenarioVersion(), caseConfiguration().getDependencies()));
        root.put("extend_entry_header", extendEntryHeader());

        root.put("docker_compose_file", outputDir() + File.separator + "docker-compose.yml");
        root.put("build_id", dockerImageVersion());

        final StringBuilder removeImagesScript = new StringBuilder();
        final ArrayList<String> links = Lists.newArrayList();
        if (caseConfiguration().getDependencies() != null) {
            caseConfiguration().getDependencies().forEach((name, service) -> {
                links.add(service.getHostname());
                if (service.isRemoveOnExit()) {
                    removeImagesScript.append("docker rmi ")
                                      .append(
                                          service.getImage().replace("${CASE_SERVER_IMAGE_VERSION}", scenarioVersion()))
                                      .append(System.lineSeparator());
                }
            });
        }
        root.put("links", links);
        root.put("removeImagesScript", removeImagesScript.toString());

        return root;
    }

    @Override
    public String extendEntryHeader() {
        final List<RequestHeader> headers = this.configuration.getExtendEntryHeader();
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder headerString = new StringBuilder("\"");
        for (RequestHeader header : headers) {
            headerString.append(" -H ").append(header.getKey()).append(":").append(header.getValue()).append(" ");
        }
        return headerString.append("\"").toString();
    }

    protected List<DockerService> convertDockerServices(final String version,
                                                        Map<String, DependencyComponent> componentMap) {
        final ArrayList<DockerService> services = Lists.newArrayList();
        if (componentMap == null) {
            return services;
        }
        componentMap.forEach((name, dependency) -> {
            DockerService service = new DockerService();

            String imageName = dependency.getImage().replace("${CASE_SERVER_IMAGE_VERSION}", version);
            service.setName(name);
            service.setImageName(imageName);
            service.setExpose(dependency.getExpose());
            service.setLinks(dependency.getDependsOn());
            service.setStartScript(dependency.getStartScript());
            service.setHostname(dependency.getHostname());
            service.setDependsOn(dependency.getDependsOn());
            service.setEntrypoint(dependency.getEntrypoint());
            service.setHealthcheck(dependency.getHealthcheck());
            service.setEnvironment(dependency.getEnvironment());
            service.setRemoveOnExit(dependency.isRemoveOnExit());
            services.add(service);
        });
        return services;
    }
}
