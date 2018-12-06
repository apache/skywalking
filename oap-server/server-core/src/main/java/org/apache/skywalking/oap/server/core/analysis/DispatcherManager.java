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

package org.apache.skywalking.oap.server.core.analysis;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.source.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, wusheng
 */
public class DispatcherManager {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherManager.class);

    private Map<Scope, List<SourceDispatcher>> dispatcherMap;

    public DispatcherManager() {
        this.dispatcherMap = new HashMap<>();
    }

    public void forward(Source source) {
        for (SourceDispatcher dispatcher : dispatcherMap.get(source.scope())) {
            dispatcher.dispatch(source);
        }
    }

    /**
     * Scan all classes under `org.apache.skywalking` package,
     *
     * If it implement {@link org.apache.skywalking.oap.server.core.analysis.SourceDispatcher}, then, it will be added
     * into this DispatcherManager based on the Source definition.
     *
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public void scan() throws IOException, IllegalAccessException, InstantiationException {
        ClassPath classpath = ClassPath.from(this.getClass().getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            if (!aClass.isInterface() && SourceDispatcher.class.isAssignableFrom(aClass)) {
                Type[] genericInterfaces = aClass.getGenericInterfaces();
                for (Type genericInterface : genericInterfaces) {
                    ParameterizedType anInterface = (ParameterizedType)genericInterface;
                    if (anInterface.getRawType().getTypeName().equals(SourceDispatcher.class.getName())) {
                        Type[] arguments = anInterface.getActualTypeArguments();

                        if (arguments.length != 1) {
                            throw new UnexpectedException("unexpected type argument number, class " + aClass.getName());
                        }
                        Type argument = arguments[0];

                        Object source = ((Class)argument).newInstance();

                        if (!Source.class.isAssignableFrom(source.getClass())) {
                            throw new UnexpectedException("unexpected type argument of class " + aClass.getName() + ", should be `org.apache.skywalking.oap.server.core.source.Source`. ");
                        }

                        Source dispatcherSource = (Source)source;
                        SourceDispatcher dispatcher = (SourceDispatcher)aClass.newInstance();

                        Scope scope = dispatcherSource.scope();

                        List<SourceDispatcher> dispatchers = this.dispatcherMap.get(scope);
                        if (dispatchers == null) {
                            dispatchers = new ArrayList<>();
                            this.dispatcherMap.put(scope, dispatchers);
                        }

                        dispatchers.add(dispatcher);

                        logger.info("Dispatcher {} is added into Scope {}.", dispatcher.getClass().getName(), scope);
                    }
                }
            }
        }
    }
}
