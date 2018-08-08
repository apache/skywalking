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

package org.apache.skywalking.oap.server.core.worker.annotation;

import java.lang.reflect.Constructor;
import java.util.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class WorkerAnnotationContainer implements WorkerClassGetter {

    private static final Logger logger = LoggerFactory.getLogger(WorkerAnnotationContainer.class);

    private int id = 0;
    private final Map<Class<AbstractWorker>, Integer> classKeyMapping;
    private final Map<Integer, Class<AbstractWorker>> idKeyMapping;
    private final Map<Class<AbstractWorker>, AbstractWorker> classKeyInstanceMapping;
    private final Map<Integer, AbstractWorker> idKeyInstanceMapping;

    public WorkerAnnotationContainer() {
        this.classKeyMapping = new HashMap<>();
        this.idKeyMapping = new HashMap<>();
        this.classKeyInstanceMapping = new HashMap<>();
        this.idKeyInstanceMapping = new HashMap<>();
    }

    @SuppressWarnings(value = "unchecked")
    public void load(ModuleManager moduleManager, List<Class> workerClasses) throws WorkerDefineLoadException {
        if (Objects.isNull(workerClasses)) {
            return;
        }

        try {
            for (Class workerClass : workerClasses) {
                id++;
                classKeyMapping.put(workerClass, id);
                idKeyMapping.put(id, workerClass);

                Constructor<AbstractWorker> constructor = workerClass.getDeclaredConstructor(ModuleManager.class);
                AbstractWorker worker = constructor.newInstance(moduleManager);
                classKeyInstanceMapping.put(workerClass, worker);
                idKeyInstanceMapping.put(id, worker);
            }
        } catch (Throwable t) {
            throw new WorkerDefineLoadException(t.getMessage(), t);
        }
    }

    @Override public Class<AbstractWorker> getClassById(int workerId) {
        return idKeyMapping.get(id);
    }

    public int findIdByClass(Class workerClass) {
        return classKeyMapping.get(workerClass);
    }

    public AbstractWorker findInstanceByClass(Class workerClass) {
        return classKeyInstanceMapping.get(workerClass);
    }

    public AbstractWorker findInstanceById(int id) {
        return idKeyInstanceMapping.get(id);
    }
}
