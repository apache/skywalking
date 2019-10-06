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
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerComposeV2RunningGenerator extends AbstractRunningGenerator {
    private static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    protected DockerComposeV2RunningGenerator() {
    }

    @Override
    public void generateAdditionFiles(IConfiguration configuration) {
        Map<String, Object> root = new HashMap<>();
        root.put("agent_home", configuration.agentHome());
        root.put("scenario_home", configuration.scenarioHome());

        root.put("scenario_name", configuration.scenarioName());
        root.put("scenario_version", configuration.scenarioVersion());
        root.put("entry_service", configuration.entryService());
        root.put("health_check", configuration.healthCheck());
        root.put("test_framework", configuration.testFramework());
        root.put("docker_image_name", configuration.dockerImageName());
        root.put("docker_container_name", configuration.dockerContainerName());

        root.put("server_addr", configuration.serverAddr());

        ArrayList<String> links = Lists.newArrayList();
        configuration.caseConfiguration().getDependencies().forEach((k, service) -> {
            links.add(service.getHost());
        });

        root.put("links", links);
        root.put("services", convertDockerServices(configuration.caseConfiguration().getDependencies()));

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

    protected List<DockerService> convertDockerServices(Map<String, DependencyComponent> componentMap) {
        ArrayList<DockerService> services = Lists.newArrayList();
        componentMap.forEach((name, dependency) -> {
            DockerService service = new DockerService();
            service.setName(name);
            service.setVersion(dependency.getVersion());
            service.setImage(dependency.getImage());
            service.setHost(dependency.getHost());
            service.setExpose(nullToEmpty(dependency.getExpose()));
            service.setVolumes(nullToEmpty(dependency.getVolumes()));
            service.setEnvironments(nullToEmpty(dependency.getEnvironment()));
            services.add(service);
        });
        return services;
    }
    private static final List<String> nullToEmpty(List<String> list) {
        return list == null ? Lists.newArrayList() : list;
    }

    @Override
    public String runningScript(IConfiguration configuration) {
        return String.format("docker-compose -f %s up", configuration.outputDir() + File.separator +
                "docker-compose.yml");
    }
}
