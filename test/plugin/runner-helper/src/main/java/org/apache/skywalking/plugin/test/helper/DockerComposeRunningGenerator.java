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

import com.google.common.collect.Lists;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.helper.vo.DependencyComponent;
import org.apache.skywalking.plugin.test.helper.vo.DockerService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerComposeRunningGenerator extends AbstractRunningGenerator {
    private static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    protected DockerComposeRunningGenerator() {
    }

    @Override
    public void generateAdditionFiles(IConfiguration configuration) {
        Map<String, Object> root = new HashMap<>();
        root.put("agent_home", configuration.agentHome());
        root.put("scenario_home", configuration.scenarioHome());

        root.put("scenario_name", configuration.scenarioName());
        root.put("scenario_version", configuration.scenarioVersion());
        root.put("entry_service", configuration.entryService());
        root.put("start_script", configuration.startScript());
        root.put("health_check", configuration.healthCheck());

        root.put("expose", configuration.caseConfiguration().getExpose());
        root.put("hostname", configuration.caseConfiguration().getHostname());
        root.put("depends_on", configuration.caseConfiguration().getDepends_on());
        root.put("environments", configuration.caseConfiguration().getEnvironment());

        root.put("docker_image_name", configuration.dockerImageName());
        root.put("docker_image_version", configuration.dockerImageVersion());
        root.put("docker_container_name", configuration.dockerContainerName());

        root.put("network_name", configuration.dockerNetworkName());

        ArrayList<String> links = Lists.newArrayList();
        configuration.caseConfiguration().getDependencies().forEach((k, service) -> {
            links.add(service.getHostname());
        });

        root.put("links", links);
        root.put("services", convertDockerServices(configuration.scenarioVersion(),
                configuration.caseConfiguration().getDependencies()));

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_28);
        try {
            cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "/");
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
        } catch (Exception e) {
            // never to do this
        }
        try {
            cfg.getTemplate("docker-compose.template").process(root, new FileWriter(new File(configuration.outputDir(),
                    "docker-compose.yml")));
        } catch (TemplateException | IOException e) {
            logger.error(e);
        }
    }

    protected List<DockerService> convertDockerServices(final String version, Map<String, DependencyComponent> componentMap) {
        ArrayList<DockerService> services = Lists.newArrayList();
        componentMap.forEach((name, dependency) -> {
            DockerService service = new DockerService();

            String imageName = dependency.getImage()
                    .replace("${CASE_SERVER_IMAGE_VERSION}", version);
            service.setName(name);
            service.setImageName(imageName);
            service.setExpose(dependency.getExpose());
            service.setLinks(dependency.getDepends_on());
            service.setHostname(dependency.getHostname());
            service.setDepends_on(dependency.getDepends_on());
            service.setEntrypoint(dependency.getEntrypoint());
            service.setHealthcheck(dependency.getHealthcheck());
            service.setEnvironment(dependency.getEnvironment());
            services.add(service);
        });
        return services;
    }

    @Override
    public String runningScript(IConfiguration configuration) {
        String docker_compose_file = configuration.outputDir() + File.separator + "docker-compose.yml";

        Map<String, Object> root = new HashMap<>();
        root.put("scenario_name", configuration.scenarioName());
        root.put("scenario_home", configuration.scenarioHome());
        root.put("scenario_version", configuration.scenarioVersion());
        root.put("docker_compose_file", docker_compose_file);
        root.put("build_id", configuration.dockerImageVersion());
        root.put("docker_container_name", configuration.dockerContainerName());
        StringWriter out = null;

        try {
            out = new StringWriter();
            cfg.getTemplate("compose-start-script.template").process(root, out);
        } catch (Exception e) {
            logger.error("Failed to generate running script.", e);
        }
        return out.toString();
    }
}
