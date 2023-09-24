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

package org.apache.skywalking.graal;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import org.apache.skywalking.aop.server.receiver.mesh.MeshOALDefine;
import org.apache.skywalking.oal.rt.OALRuntime;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.oal.rt.CoreOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.DisableOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserOALDefine;
import org.apache.skywalking.oap.server.receiver.clr.provider.CLROALDefine;

import org.apache.skywalking.oap.server.receiver.envoy.TCPOALDefine;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.mx.FieldsHelper;
import org.apache.skywalking.oap.server.receiver.jvm.provider.JVMOALDefine;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;

import java.util.List;

public class Generator {

    public static void generateOALClass(String rootPath) throws ModuleStartException, OALCompileException {
        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        try {
            scopeScan.scan();
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        List<OALEngine> oalEngines = new ArrayList<>();
        oalEngines.add(new OALRuntime(DisableOALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(CoreOALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(TCPOALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(BrowserOALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(JVMOALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(CLROALDefine.INSTANCE));
        oalEngines.add(new OALRuntime(MeshOALDefine.INSTANCE));
        for (OALEngine engine :oalEngines) {
            OALRuntime oalRuntime = (OALRuntime) engine;
            oalRuntime.setStorageBuilderFactory(new StorageBuilderFactory.Default());
            oalRuntime.generateOALClassFiles(OALEngineLoaderService.class.getClassLoader(), rootPath);
        }
    }

    public static void generateForEnvoyMetric() throws ModuleStartException {
        try {
            FieldsHelper.SINGLETON.init("metadata-service-mapping.yaml", ServiceMetaInfo.class);
        } catch (Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }
    }

    public static void generateForOALAnnotation(String path) throws IOException, CannotCompileException, NotFoundException {
        ClassPath classpath = ClassPath.from(ResourceUtils.class.getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        List<Class> annotationStreamClass = new ArrayList<>();
        List<Class> annotationScopeDeclarationClass = new ArrayList<>();
        classes.forEach(classInfo -> {
            Class<?> clazz = classInfo.load();
            if (clazz.isAnnotationPresent(Stream.class)) {
                annotationStreamClass.add(clazz);
            }
            if (clazz.isAnnotationPresent(ScopeDeclaration.class)) {
                annotationScopeDeclarationClass.add(clazz);
            }
        });

        ClassPool pool = ClassPool.getDefault();

        CtClass ctClass = pool.makeClass("org.apache.skywalking.oap.graal.ScannedClasses");

        CtField listField1 = CtField.make(getFieldString("streamClasses", annotationStreamClass), ctClass);
        CtField listField2 = CtField.make(getFieldString("scopeDeclarationClass", annotationScopeDeclarationClass), ctClass);

        ctClass.addField(listField1);
        ctClass.addField(listField2);

        String generatedFilePath = path + File.separator + "classes";
        ctClass.writeFile(generatedFilePath);
    }

    private static String getFieldString(String fieldName, List<Class> classes) {
        StringBuilder listFieldTemplate = new StringBuilder("public static java.util.List " + fieldName +  " = java.util.Arrays.asList( new java.lang.Class[] {");
        for (Class clazz: classes) {
            listFieldTemplate.append(clazz.getName());
            listFieldTemplate.append(".class,");
        }
        listFieldTemplate.deleteCharAt(listFieldTemplate.length() - 1);
        listFieldTemplate.append("} );");
        return listFieldTemplate.toString();
    }

}
