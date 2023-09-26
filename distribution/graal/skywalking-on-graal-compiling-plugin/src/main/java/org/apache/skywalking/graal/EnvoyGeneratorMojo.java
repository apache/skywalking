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
 *
 */

package org.apache.skywalking.graal;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.mx.FieldsHelper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Mojo(name = "envoy-generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EnvoyGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() {
        String targetDirectory = project.getBuild().getDirectory();
        try {
            generateForEnvoyMetric(getConfigFilePath(targetDirectory));
        } catch (ModuleStartException e) {
            throw new RuntimeException(e);
        }
    }

    private static void generateForEnvoyMetric(String rootPath) throws ModuleStartException {
        try {
            FieldsHelper.SINGLETON.init(Files.newInputStream(Paths.get(rootPath + "metadata-service-mapping.yaml")), ServiceMetaInfo.class);
        } catch (Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }
    }

    private static String getConfigFilePath(String targetDirectory) {
        return targetDirectory + File.separator + ".." + File.separator +
                ".." + File.separator + ".." + File.separator + ".." + File.separator +
                "oap-server" + File.separator + "server-starter" + File.separator +
                "target" + File.separator + "classes" + File.separator;
    }

}
