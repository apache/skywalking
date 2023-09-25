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
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.util.OALClassGenerator;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageException;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * OAL Runtime is the class generation engine, which load the generated classes from OAL scrip definitions. This runtime
 * is loaded dynamically.
 */
@Slf4j
public class OALRuntime implements OALEngine {

    private static boolean IS_RT_TEMP_FOLDER_INIT_COMPLETED = false;

    private OALClassGenerator oalClassGenerator;

    private StreamAnnotationListener streamAnnotationListener;
    private DispatcherDetectorListener dispatcherDetectorListener;
    private final List<Class> metricsClasses;
    private final List<Class> dispatcherClasses;

    private final OALDefine oalDefine;

    public OALRuntime(OALDefine define) {
        oalDefine = define;
        metricsClasses = new ArrayList<>();
        dispatcherClasses = new ArrayList<>();

        oalClassGenerator = new OALClassGenerator(define);
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
        oalClassGenerator.setStorageBuilderFactory(factory);
    }

    @Override
    public void start(ClassLoader currentClassLoader) throws ModuleStartException, OALCompileException {
        if (!IS_RT_TEMP_FOLDER_INIT_COMPLETED) {
            oalClassGenerator.prepareRTTempFolder();
            IS_RT_TEMP_FOLDER_INIT_COMPLETED = true;
        }

        Reader read;
        oalClassGenerator.setCurrentClassLoader(currentClassLoader);

        try {
            read = ResourceUtils.read(oalDefine.getConfigFile());
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate " + oalDefine.getConfigFile(), e);
        }

        OALScripts oalScripts;
        try {
            ScriptParser scriptParser = ScriptParser.createFromFile(read, oalDefine.getSourcePackage());
            oalScripts = scriptParser.parse();
        } catch (IOException e) {
            throw new ModuleStartException("OAL script parse analysis failure.", e);
        }

        oalClassGenerator.generateClassAtRuntime(oalScripts, metricsClasses, dispatcherClasses);
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
}
