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
import freemarker.template.TemplateExceptionHandler;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.plugin.test.helper.exception.GenerateFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRunningGenerator implements ScenarioRunningScriptGenerator {

    private Logger logger = LoggerFactory.getLogger(AbstractRunningGenerator.class);
    protected final Configuration cfg;

    protected AbstractRunningGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_28);
        try {
            cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "/");
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
        } catch (Exception e) {
            // never to do this
        }
    }

    @Override
    public final void generate(IConfiguration configuration) throws GenerateFailedException {
        generateAdditionFiles(configuration);

        Map<String, Object> root = new HashMap<>();
        root.put("running_script", runningScript(configuration));
        root.put("scenario_name", configuration.scenarioName());
        root.put("scenario_version", configuration.scenarioVersion());

        try {
            cfg.getTemplate("scenario.sh").process(root,
                new FileWriter(new File(configuration.outputDir() + File.separator + "scenario.sh")));
        } catch (Exception e) {
            logger.error("Failed to write scenario.sh", e);
        }
    }

    public abstract void generateAdditionFiles(IConfiguration configuration) throws GenerateFailedException;

    public abstract String runningScript(IConfiguration configuration);
}
