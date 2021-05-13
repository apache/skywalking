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

import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockerContainerRunningGenerator extends AbstractRunningGenerator {
    private static final Logger LOGGER = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    protected DockerContainerRunningGenerator() {
    }

    @Override
    public void generateAdditionFiles(IConfiguration configuration) {
        // DO Nothing
    }

    @Override
    public String runningScript(IConfiguration configuration) {
        final Map<String, Object> root = configuration.toMap();
        final StringWriter out = new StringWriter();

        try {
            cfg.getTemplate("container-start-script.template").process(root, out);
        } catch (Exception e) {
            LOGGER.error("Failed to generate running script.", e);
        }
        return out.toString();
    }
}
