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

package org.apache.skywalking.oap.graal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.reflect.ClassPath;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NativeConfigFilter {

    // related https://github.com/oracle/graal/issues/4797
    private static Set<String> UNSUPPORTED_ELEMENTS = Set.of("jdk.internal.loader.BuiltinClassLoader",
            "jdk.internal.loader.ClassLoaders$AppClassLoader",
            "jdk.internal.loader.ClassLoaders$PlatformClassLoader"
    );

    private static Set<String> NEED_REFLECT_PACKAGE_NAME = Set.of(
            "org.apache.skywalking.oap.server.core.query",
            "org.apache.skywalking.oap.query.graphql.resolver",
            "org.apache.skywalking.oap.query.graphql.type"
    );

    public static void main(String[] args) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        URL resourceUrl = NativeConfigFilter.class
                .getClassLoader().getResource("META-INF/native-image/main/reflect-config.json");
        if (resourceUrl == null) {
            throw new IllegalArgumentException("File not found!");
        }
        File jsonFile = new File(resourceUrl.getFile());

        ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(jsonFile);

        List<String> filteredClasses = findAllSubclasses(ModuleConfig.class);

        NEED_REFLECT_PACKAGE_NAME.forEach(name -> {
            List<String> allClassUnderPackage = findAllClassUnderPackage(name);
            filteredClasses.addAll(allClassUnderPackage);
        });

        List<ObjectNode> objectNodes = filteredClasses.stream()
                .map(className -> generateConfig(className))
                .collect(Collectors.toList());
        List<String> objectNodesName = objectNodes.stream()
                .map(objectNode -> objectNode.get("name").asText()).collect(Collectors.toList());

        List<JsonNode> elementsToKeep = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            String name = element.get("name").asText();
            if (!UNSUPPORTED_ELEMENTS.contains(name) && !objectNodesName.contains(name)) {
                elementsToKeep.add(element);
            }
        }
        elementsToKeep.addAll(objectNodes);

        ArrayNode newArrayNode = objectMapper.valueToTree(elementsToKeep);

        objectMapper.writeValue(jsonFile, newArrayNode);
    }

    private static List<String> findAllClassUnderPackage(String packageName) {
        List<String> subclasses = new ArrayList<>();
        ClassPath classpath;
        try {
            classpath = ClassPath.from(NativeConfigFilter.class.getClassLoader());
            for (ClassPath.ClassInfo classInfo : classpath.getTopLevelClassesRecursive(packageName)) {
                Class<?> clazz = classInfo.load();
                subclasses.add(clazz.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return subclasses;
    }

    private static List<String> findAllSubclasses(Class<?> baseClass) {
        List<String> subclasses = new ArrayList<>();
        ClassPath classpath;

        try {
            classpath = ClassPath.from(baseClass.getClassLoader());
            for (ClassPath.ClassInfo classInfo : classpath.getTopLevelClassesRecursive("org.apache.skywalking")) {
                Class<?> clazz = classInfo.load();
                if (baseClass.isAssignableFrom(clazz) && !baseClass.equals(clazz)) {
                    subclasses.add(classInfo.getName());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return subclasses;
    }

    private static ObjectNode generateConfig(String className) {
        JsonNodeFactory nodeFactory = JsonNodeFactory.instance;

        ObjectNode rootNode = nodeFactory.objectNode();

        //"queryAllDeclaredConstructors" : true,
        //    "queryAllPublicConstructors" : true,
        //    "queryAllDeclaredMethods" : true,
        //    "queryAllPublicMethods" : true,
        //    "allDeclaredClasses" : true,
        //    "allPublicClasses" : true
        rootNode.put("name", className);
        rootNode.put("allDeclaredFields", true);
//        rootNode.put("allPublicFields", true);
        rootNode.put("allDeclaredClasses", true);
//        rootNode.put("allPublicClasses", true);
        rootNode.put("allDeclaredMethods", true);
        rootNode.put("allDeclaredConstructors", true);
//        ArrayNode methodsNode = nodeFactoy.arrayNode();
//
//        ObjectNode methodNode = nodeFactory.objectNode();
//        methodNode.put("name", "<init>");
//
//        methodNode.set("parameterTypes", nodeFactory.arrayNode());
//        methodsNode.add(methodNode);
//
//        rootNode.set("methods", methodsNode);

        return rootNode;
    }
}
