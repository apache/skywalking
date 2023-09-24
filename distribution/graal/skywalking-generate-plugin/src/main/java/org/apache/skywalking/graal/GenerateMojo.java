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

import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.apache.maven.plugin.AbstractMojo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.skywalking.oal.rt.GeneratedFileConfiguration;

import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() {
        try {
            String targetDirectory = project.getBuild().getDirectory();
            String generatedFilePath = getGeneratedFilePath(targetDirectory);
            GeneratedFileConfiguration.setGeneratedFilePath(generatedFilePath);
            Generator.generateOALClass(targetDirectory);
            Generator.generateForOALAnnotation(targetDirectory);

        } catch (OALCompileException | ModuleStartException | NotFoundException | IOException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static String getGeneratedFilePath(String targetDirectory) {
        return targetDirectory + File.separator + "classes" +
                File.separator + "org" + File.separator + "apache" +
                File.separator + "skywalking" + File.separator + "oap" +
                File.separator + "server" + File.separator + "core" +
                File.separator + "source" + File.separator + "oal" +
                File.separator + "rt";
    }
}
