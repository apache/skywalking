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

package org.apache.skywalking.apm.agent.core.plugin.loader;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;

/**
 * The <code>AgentClassLoader</code> represents a classloader,
 * which is in charge of finding plugins and interceptors.
 *
 * @author wusheng
 */
public class AgentClassLoader extends ClassLoader {

    static {
        tryRegisterAsParallelCapable();
    }

    private static final ILog logger = LogManager.getLogger(AgentClassLoader.class);
    /**
     * The default class loader for the agent.
     */
    private static AgentClassLoader DEFAULT_LOADER;

    private List<File> classpath;
    private List<Jar> allJars;
    private ReentrantLock jarScanLock = new ReentrantLock();

    /**
     * Functional Description: solve the classloader dead lock when jvm start
     * only support JDK7+, since ParallelCapable appears in JDK7+
     */
    private static void tryRegisterAsParallelCapable() {
        Method[] methods = ClassLoader.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String methodName = method.getName();
            if ("registerAsParallelCapable".equalsIgnoreCase(methodName)) {
                try {
                    method.setAccessible(true);
                    method.invoke(null);
                } catch (Exception e) {
                    logger.warn(e, "can not invoke ClassLoader.registerAsParallelCapable()");
                }
                return;
            }
        }
    }

    public static AgentClassLoader getDefault() {
        return DEFAULT_LOADER;
    }

    /**
     * Init the default
     *
     * @return
     * @throws AgentPackageNotFoundException
     */
    public static AgentClassLoader initDefaultLoader() throws AgentPackageNotFoundException {
        if (DEFAULT_LOADER == null) {
            synchronized (AgentClassLoader.class) {
                if (DEFAULT_LOADER == null) {
                    DEFAULT_LOADER = new AgentClassLoader(PluginBootstrap.class.getClassLoader());
                }
            }
        }
        return getDefault();
    }

    public AgentClassLoader(ClassLoader parent) throws AgentPackageNotFoundException {
        super(parent);
        File agentDictionary = AgentPackagePath.getPath();
        classpath = new LinkedList<File>();
        classpath.add(new File(agentDictionary, "plugins"));
        classpath.add(new File(agentDictionary, "activations"));
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Jar> allJars = getAllJars();
        String path = name.replace('.', '/').concat(".class");
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(path);
            if (entry != null) {
                try {
                    URL classFileUrl = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + path);
                    byte[] data = null;
                    BufferedInputStream is = null;
                    ByteArrayOutputStream baos = null;
                    try {
                        is = new BufferedInputStream(classFileUrl.openStream());
                        baos = new ByteArrayOutputStream();
                        int ch = 0;
                        while ((ch = is.read()) != -1) {
                            baos.write(ch);
                        }
                        data = baos.toByteArray();
                    } finally {
                        if (is != null)
                            try {
                                is.close();
                            } catch (IOException ignored) {
                            }
                        if (baos != null)
                            try {
                                baos.close();
                            } catch (IOException ignored) {
                            }
                    }
                    return defineClass(name, data, 0, data.length);
                } catch (MalformedURLException e) {
                    logger.error(e, "find class fail.");
                } catch (IOException e) {
                    logger.error(e, "find class fail.");
                }
            }
        }
        throw new ClassNotFoundException("Can't find " + name);
    }

    @Override
    protected URL findResource(String name) {
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                try {
                    return new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name);
                } catch (MalformedURLException e) {
                    continue;
                }
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> allResources = new LinkedList<URL>();
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                allResources.add(new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name));
            }
        }

        final Iterator<URL> iterator = allResources.iterator();
        return new Enumeration<URL>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public URL nextElement() {
                return iterator.next();
            }
        };
    }

    private List<Jar> getAllJars() {
        if (allJars == null) {
            jarScanLock.lock();
            try {
                if (allJars == null) {
                    allJars = new LinkedList<Jar>();
                    for (File path : classpath) {
                        if (path.exists() && path.isDirectory()) {
                            String[] jarFileNames = path.list(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith(".jar");
                                }
                            });
                            for (String fileName : jarFileNames) {
                                try {
                                    File file = new File(path, fileName);
                                    Jar jar = new Jar(new JarFile(file), file);
                                    allJars.add(jar);
                                    logger.info("{} loaded.", file.toString());
                                } catch (IOException e) {
                                    logger.error(e, "{} jar file can't be resolved", fileName);
                                }
                            }
                        }
                    }
                }
            } finally {
                jarScanLock.unlock();
            }
        }

        return allJars;
    }

    private class Jar {
        private JarFile jarFile;
        private File sourceFile;

        private Jar(JarFile jarFile, File sourceFile) {
            this.jarFile = jarFile;
            this.sourceFile = sourceFile;
        }
    }
}
