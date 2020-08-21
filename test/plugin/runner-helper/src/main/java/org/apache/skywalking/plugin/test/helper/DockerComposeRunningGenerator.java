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

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Map;

public class DockerComposeRunningGenerator extends AbstractRunningGenerator {
    private static final Logger LOGGER = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    protected DockerComposeRunningGenerator() {
    }

    @Override
    public void generateAdditionFiles(IConfiguration configuration) {
        final Map<String, Object> root = configuration.toMap();

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
            cfg.getTemplate("docker-compose.template")
               .process(root, new FileWriter(new File(configuration.outputDir(), "docker-compose.yml")));
        } catch (TemplateException | IOException e) {
            LOGGER.error(e);
        }
    }

    @Override
    public String runningScript(IConfiguration configuration) {
        final Map<String, Object> root = configuration.toMap();

        StringWriter out = new StringWriter();

        try {
            cfg.getTemplate("compose-start-script.template").process(root, out);
        } catch (Exception e) {
            LOGGER.error("Failed to generate running script.", e);
        }
        return out.toString();
    }
}
