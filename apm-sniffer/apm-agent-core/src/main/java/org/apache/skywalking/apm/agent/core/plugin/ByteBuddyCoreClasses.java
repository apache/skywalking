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

package org.apache.skywalking.apm.agent.core.plugin;

/**
 * All ByteBuddy core classes required to expose, including open edge for JDK 9+ module, or Bootstrap instrumentation.
 */
public class ByteBuddyCoreClasses {
    private static final String SHADE_PACKAGE = "org.apache.skywalking.apm.dependencies.";

    public static final String[] CLASSES = {
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.RuntimeType",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.This",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.AllArguments",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.AllArguments$Assignment",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.SuperCall",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.Origin",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.Morph",
        };
}
