/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.plugin.loader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarFile;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * The <code>PluginClassLoader</code> represents a classloader,
 * which is in charge of finding plugins and interceptors.
 *
 * @author wusheng
 */
public class PluginClassLoader extends ClassLoader {
    private static final ILog logger = LogManager.getLogger(PluginClassLoader.class);
    private static PluginClassLoader LOADER;

    private List<File> classpath;
    private List<JarFile> allJars;
    private ReentrantLock jarScanLock = new ReentrantLock();

    public static PluginClassLoader get() {
        return LOADER;
    }

    public static PluginClassLoader initAndGet(File agentDictionary) {
        LOADER = new PluginClassLoader(agentDictionary);
        return get();
    }

    private PluginClassLoader(File agentDictionary) {
        super(PluginClassLoader.class.getClassLoader());
        classpath = new LinkedList<File>();
        classpath.add(new File(agentDictionary, "plugins"));
        classpath.add(new File(agentDictionary, "activations"));
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name) {
        getAllJars();
        throw new RuntimeException("");
    }

    private List<JarFile> getAllJars() {
        if (allJars == null) {
            jarScanLock.lock();
            try {
                if (allJars == null) {
                    allJars = new LinkedList<JarFile>();
                    for (File path : classpath) {
                        if (path.exists() && path.isDirectory()) {
                            String[] jarFileNames = path.list(new FilenameFilter() {
                                @Override public boolean accept(File dir, String name) {
                                    return name.endsWith(".jar");
                                }
                            });
                            for (String fileName : jarFileNames) {
                                try {
                                    JarFile jar = new JarFile(fileName);
                                    allJars.add(jar);
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
}
