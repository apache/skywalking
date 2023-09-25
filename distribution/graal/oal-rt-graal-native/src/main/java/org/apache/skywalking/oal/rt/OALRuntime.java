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

package org.apache.skywalking.oal.rt;

import lombok.extern.slf4j.Slf4j;

import org.apache.skywalking.oal.rt.util.OALClassGenerator;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageException;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;

import java.io.File;
import java.io.IOException;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import java.util.Map;
import java.util.Objects;


/**
 * Changed the implementation of the OAL kernel due to the Graal limitation on runtime class generation.
 * Now, class generation will be advanced to compile time, see (@link org.apache.skywalking.graal.OALGenerator) and loaded at runtime.
 */
@Slf4j
public class OALRuntime implements OALEngine {

    private StreamAnnotationListener streamAnnotationListener;
    private DispatcherDetectorListener dispatcherDetectorListener;
    private final List<Class> metricsClasses;
    private final List<Class> dispatcherClasses;

    private final OALDefine oalDefine;

    // change
    private static boolean INITIALED = false;

    //change end

    public OALRuntime(OALDefine define) {
        oalDefine = define;
        metricsClasses = new ArrayList<>();
        dispatcherClasses = new ArrayList<>();
    }

    @Override
    public void setStreamListener(StreamAnnotationListener listener) throws ModuleStartException {
        this.streamAnnotationListener = listener;
    }

    @Override
    public void setDispatcherListener(DispatcherDetectorListener listener) throws ModuleStartException {
        dispatcherDetectorListener = listener;
    }

    @Override
    public void setStorageBuilderFactory(final StorageBuilderFactory factory) {

    }

    @Override
    public void notifyAllListeners() throws ModuleStartException {
        for (Class metricsClass : metricsClasses) {
            try {
                streamAnnotationListener.notify(metricsClass);
            } catch (StorageException e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
        for (Class dispatcherClass : dispatcherClasses) {
            try {
                dispatcherDetectorListener.addIfAsSourceDispatcher(dispatcherClass);
            } catch (Exception e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
    }

    // ------------------------------------ substituted method ------------------------------------
    @Override
    public void start(ClassLoader currentClassLoader) {
        if (INITIALED) {
            return;
        }
        if (Objects.equals(System.getProperty("org.graalvm.nativeimage.imagecode"), "runtime")) {
            startByNativeImage(currentClassLoader);
        } else {
            startByJar(currentClassLoader);
        }
        INITIALED = true;
    }

    private void startByNativeImage(ClassLoader currentClassLoader) {
        try (FileSystem  fileSystem = FileSystems.newFileSystem(URI.create("resource:/"), Map.of(), currentClassLoader)) {
            String basePackage = OALClassGenerator.getGeneratedFilePath();
            String metricsPath = basePackage + File.separator + "metrics";
            String dispatcherPath = basePackage + File.separator + "dispatcher";
            String metricsBuilderPath = metricsPath + File.separator + "builder";

            Path metrics = fileSystem.getPath(metricsPath);
            Path dispatcher = fileSystem.getPath(dispatcherPath);

            try (java.util.stream.Stream<Path> files = Files.walk(metrics)) {
                files.forEach(file -> {
                    if (!file.toString().endsWith(".class")) {
                        return;
                    }
                    String name = file.toString().replace(File.separator, ".");

                    name = name.substring(0, name.length() - ".class".length());
                    if (name.startsWith(metricsBuilderPath.replace(File.separatorChar, '.'))) {
                        return;
                    }
                    try {
                        Class<?> aClass = Class.forName(name);
                        if (!aClass.isAnnotationPresent(Stream.class)) {
                            return;
                        }
                        metricsClasses.add(aClass);
                    } catch (ClassNotFoundException e) {
                        // should not reach here
                        log.error(e.getMessage());
                    }
                });
            }
            catch (IOException e) {
                log.error("Failed to walk the path: " + metrics, e);
            }
            try (java.util.stream.Stream<Path> files = Files.walk(dispatcher)) {
                files.forEach(file -> {
                    if (!file.toString().endsWith(".class")) {
                        return;
                    }
                    String name = file.toString().replace(File.separator, ".");
                    name = name.substring(0, name.length() - ".class".length());
                    try {
                        dispatcherClasses.add(Class.forName(name));
                    } catch (ClassNotFoundException e) {
                        // should not reach here
                        log.error(e.getMessage());
                    }
                });
            }
            catch (IOException e) {
                log.error("Failed to walk the path: " + dispatcher, e);
            }
        } catch (IOException e) {
            // should not reach here
            log.error("Failed to create FileSystem", e);
        }
    }

    private void startByJar(ClassLoader currentClassLoader) {
        String basePackage = OALClassGenerator.getGeneratedFilePath();
        String metricsPath = basePackage + File.separator + "metrics";
        String dispatcherPath = basePackage + File.separator + "dispatcher";
        String metricsBuilderPath = metricsPath + File.separator + "builder";

        try {
            Enumeration<URL> metricsResources = currentClassLoader.getResources(metricsPath);
            while (metricsResources.hasMoreElements()) {
                URL resource = metricsResources.nextElement();
                processResourcePath(resource, metricsClasses, metricsBuilderPath.replace(File.separatorChar, '.'));
            }
        } catch (IOException e) {
            log.error("Failed to locate resource " + metricsPath + " on classpath");
        }

        try {
            Enumeration<URL> dispatcherResources = currentClassLoader.getResources(dispatcherPath);
            while (dispatcherResources.hasMoreElements()) {
                URL resource = dispatcherResources.nextElement();
                processResourcePath(resource, dispatcherClasses, null);
            }
        } catch (IOException e) {
            log.error("Failed to locate resource " + dispatcherPath + " on classpath");
        }
    }

    private void processResourcePath(URL resource, List<Class> classes, String excludeStartWith) {
        try {
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                File dir = new File(resource.getFile());
                if (dir.isDirectory()) {
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.getName().endsWith(".class")) {
                            String className = file.getPath().replace(File.separatorChar, '.');
                            className = className.substring(0, className.length() - ".class".length());
                            if (excludeStartWith != null && className.startsWith(excludeStartWith)) {
                                continue;
                            }
                            Class<?> aClass = Class.forName(className);
                            if (aClass.isAnnotationPresent(Stream.class)) {
                                classes.add(aClass);
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // should not reach here
            log.error(e.getMessage());
        }
    }

}
