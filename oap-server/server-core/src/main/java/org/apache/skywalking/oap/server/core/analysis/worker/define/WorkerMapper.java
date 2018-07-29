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

package org.apache.skywalking.oap.server.core.analysis.worker.define;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.worker.Worker;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * @author peng-yongsheng
 */
public class WorkerMapper implements Service {

    private int id = 0;
    private final ModuleManager moduleManager;
    private final Map<Class<Worker>, Integer> classKeyMapping;
    private final Map<Integer, Class<Worker>> idKeyMapping;
    private final Map<Class<Worker>, Worker> classKeyInstanceMapping;
    private final Map<Integer, Worker> idKeyInstanceMapping;

    public WorkerMapper(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.classKeyMapping = new HashMap<>();
        this.idKeyMapping = new HashMap<>();
        this.classKeyInstanceMapping = new HashMap<>();
        this.idKeyInstanceMapping = new HashMap<>();
    }

    @SuppressWarnings(value = "unchecked")
    public void load() throws WorkerDefineLoadException {
        URL url = Resources.getResource("META-INF/defines/worker.def");

        try {
            List<String> lines = Resources.readLines(url, Charsets.UTF_8);

            for (String line : lines) {
                Class<Worker> workerClass = (Class<Worker>)Class.forName(line);
                id++;
                classKeyMapping.put(workerClass, id);
                idKeyMapping.put(id, workerClass);

                Constructor<Worker> constructor = workerClass.getDeclaredConstructor(ModuleManager.class);
                Worker worker = constructor.newInstance(moduleManager);
                classKeyInstanceMapping.put(workerClass, worker);
            }
        } catch (Exception e) {
            throw new WorkerDefineLoadException(e.getMessage(), e);
        }
    }

    public int findIdByClass(Class workerClass) {
        return classKeyMapping.get(workerClass);
    }

    public Class<Worker> findClassById(int id) {
        return idKeyMapping.get(id);
    }

    public Worker findInstanceByClass(Class workerClass) {
        return classKeyInstanceMapping.get(workerClass);
    }

    public Worker findInstanceById(int id) {
        return idKeyInstanceMapping.get(id);
    }
}
