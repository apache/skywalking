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

package org.apache.skywalking.apm.agent.core.jvm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public class JVMUtil {

    private static final ILog LOGGER = LogManager.getLogger(JVMUtil.class);

    private static Map<String, String> JAR_PATH_MAP = new HashMap<>();

    private static Set<String> JAR_FILE_LIST = new HashSet<>();

    private static List<String> getJarFileNameList() {
        List<String> jarFileNameList = new ArrayList<>();
        for (String jarFile : JAR_FILE_LIST) {
            String jarFileName = jarFile.substring(jarFile.lastIndexOf("/") + 1);
            jarFileNameList.add(jarFileName);
        }
        Collections.sort(jarFileNameList);
        return jarFileNameList;
    }

    public static void loadJvmInfo(Instrumentation instrumentation) {
        loadJarPath(instrumentation);
        loadJarFileList();
    }

    private static void loadJarPath(Instrumentation instrumentation) {
        Class[] clzzs = instrumentation.getAllLoadedClasses();
        Set<ClassLoader> classLoaders = new HashSet<>();
        for (final Class clzz : clzzs) {
            ClassLoader classLoader = clzz.getClassLoader();
            if (classLoader != null) {
                classLoaders.add(clzz.getClassLoader());
            }
        }

        for (ClassLoader classLoader : classLoaders) {
            classResCalc(classLoader.getClass());
        }
    }

    private static void classResCalc(Class c) {
        try {
            java.net.URL url = c.getResource("/");
            if (null != url) {
                JAR_PATH_MAP.put(url.toString(), "");
            }
        } catch (Exception e) {
            LOGGER.warn("Error msg: {}", e.getMessage());
        }
    }

    private static void loadJarFileList() {
        String[] classPathJar = getClasspath().split(OSUtil.getPathSeparator());
        for (String s : classPathJar) {
            if (!s.endsWith(".jar")) {
                continue;
            }
            String path = new File(s).getAbsolutePath();
            JAR_FILE_LIST.add(path);
        }
        for (String s : JAR_PATH_MAP.keySet()) {
            s = s.replace("file:", "");
            if (s.endsWith("WEB-INF/classes/")) {
                s = s.replace("WEB-INF/classes/", "WEB-INF/lib/");
            }
            File tempDir = new File(s);
            if (!tempDir.exists() || !tempDir.isDirectory()) {
                continue;
            }
            for (File file : tempDir.listFiles()) {
                if (!file.getName().endsWith(".jar")) {
                    continue;
                }
                JAR_FILE_LIST.add(file.getAbsolutePath());
            }
        }
        loadSpringBootLib();
    }

    private static void loadSpringBootLib() {
        List<String> springBootJars = new ArrayList<>();
        for (String s : JAR_FILE_LIST) {
            JarFile jar = null;
            try {
                jar = new JarFile(s);
                if (jar.getManifest() == null || !"org.springframework.boot.loader.JarLauncher".equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))) {
                    continue;
                }
                Enumeration<JarEntry> jarEntry = jar.entries();
                String jarPath;
                while (jarEntry.hasMoreElements()) {
                    jarPath = jarEntry.nextElement().getName();
                    if (jarPath.endsWith(".jar")) {
                        springBootJars.add(s + "!/" + jarPath);
                    }
                }

            } catch (Exception e) {
                LOGGER.warn("Error msg: {}", e.getMessage());
            } finally {
                try {
                    if (jar != null) {
                        jar.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn("Error msg: {}", e.getMessage());
                }
            }
        }
        if (springBootJars.size() > 0) {
            JAR_FILE_LIST.addAll(springBootJars);
        }
    }

    private static String getClasspath() {
        return System.getProperty("java.class.path");
    }

    private static List<String> getVmArgs() {
        List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (vmArgs == null) {
            return Collections.emptyList();
        }
        List<String> sortedVmArgs = new ArrayList<>(vmArgs);
        Collections.sort(sortedVmArgs);
        return sortedVmArgs;
    }

    private static String getVmStartTime() {
        long startTime;
        try {
            startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("RuntimeMXBean.getStartTime() unsupported. Caused:" + e.getMessage(), e);
            startTime = System.currentTimeMillis();
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date(startTime));
    }

    public static List<KeyStringValuePair> buildJvmInfo() {
        List<KeyStringValuePair> jvmInfo = new ArrayList<>();
        jvmInfo.add(KeyStringValuePair.newBuilder().setKey("Start Time").setValue(getVmStartTime()).build());
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        jvmInfo.add(KeyStringValuePair.newBuilder().setKey("JVM Arguments").setValue(gson.toJson(getVmArgs())).build());
        jvmInfo.add(KeyStringValuePair.newBuilder().setKey("Jar Dependencies").setValue(gson.toJson(getJarFileNameList())).build());
        return jvmInfo;
    }

}