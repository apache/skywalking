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

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;

/**
 * Disable definition scanner and register.
 */
public class DisableRegister implements AnnotationListener {
    public static DisableRegister INSTANCE = new DisableRegister();
    private Set<String> disableEntitySet = new HashSet<>();
    private final Set<String> scriptDisables = new HashSet<>();
    private final Set<String> definedStreams = new HashSet<>();
    private final Set<String> runningStreams = new HashSet<>();

    DisableRegister() {
    }

    @Override
    public Class<? extends Annotation> annotation() {
        return MultipleDisable.class;
    }

    @Override
    public void notify(Class aClass) {
        MultipleDisable annotation = (MultipleDisable) aClass.getAnnotation(MultipleDisable.class);
        Disable[] valueList = annotation.value();
        if (valueList != null) {
            for (Disable disable : valueList) {
                add(disable.value());
            }
        }
    }

    public void add(String name) {
        disableEntitySet.add(name);
    }

    /**
     * A {@code disable(name)} statement of an OAL script. Unlike a hard-coded disable, a script names its stream by
     * hand, so a name no stream carries is reported by {@link #scriptDisablesWithoutStream()}. A disable only skips a
     * stream defined after it, see {@link #isStreamRunning(String)}.
     */
    public void addFromScript(String name) {
        scriptDisables.add(name);
        add(name);
    }

    /**
     * Record a stream as it is defined, and as running unless a disable registered before skips it.
     */
    public void markStreamDefined(String name) {
        definedStreams.add(name);
        if (!include(name)) {
            runningStreams.add(name);
        }
    }

    /**
     * @return true when the stream was defined with its workers, so a disable of it would come too late.
     */
    public boolean isStreamRunning(String name) {
        return runningStreams.contains(name);
    }

    /**
     * @return the names OAL scripts disable that no defined stream carries. Read once every module has started and
     * defined its streams.
     */
    public Set<String> scriptDisablesWithoutStream() {
        final Set<String> unknown = new TreeSet<>(scriptDisables);
        unknown.removeAll(definedStreams);
        return unknown;
    }

    public boolean include(String name) {
        return disableEntitySet.contains(name);
    }

    public static class SingleDisableScanListener implements AnnotationListener {
        @Override
        public Class<? extends Annotation> annotation() {
            return Disable.class;
        }

        @Override
        public void notify(Class aClass) {
            String name = ((Disable) aClass.getAnnotation(Disable.class)).value();
            DisableRegister.INSTANCE.disableEntitySet.add(name);
        }
    }
}
