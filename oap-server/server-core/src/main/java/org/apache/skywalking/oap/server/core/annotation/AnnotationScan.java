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

package org.apache.skywalking.oap.server.core.annotation;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.*;

/**
 * @author peng-yongsheng
 */
public class AnnotationScan {

    private final List<AnnotationListener> listeners;

    public AnnotationScan() {
        this.listeners = new LinkedList<>();
    }

    public void registerListener(AnnotationListener listener) {
        listeners.add(listener);
    }

    public void scan(Runnable callBack) throws IOException {
        ClassPath classpath = ClassPath.from(this.getClass().getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            for (AnnotationListener listener : listeners) {
                if (aClass.isAnnotationPresent(listener.annotation())) {
                    listener.notify(aClass);
                }
            }
        }

        callBack.run();
    }
}
