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

package org.apache.skywalking.oap.server.core.classloader;

/**
 * Marker contract for class loaders that expose a public {@code defineClass} so generated
 * bytecode can be injected without going through Javassist's deprecated
 * {@code CtClass.toClass(ClassLoader, ProtectionDomain)} reflection path.
 *
 * <p>Background: on JDK 9+ the deprecated {@code toClass(ClassLoader, ProtectionDomain)}
 * reflectively calls {@code java.lang.ClassLoader.defineClass} via {@code setAccessible},
 * which the strong-encapsulation rule blocks at runtime with
 * {@code InaccessibleObjectException} unless the operator explicitly opens
 * {@code java.base/java.lang} via {@code --add-opens}. Static MAL/LAL boot is unaffected
 * because {@code MeterClassPackageHolder}'s package access works through Javassist's
 * neighbor-class API on the default loader. Runtime-rule's per-file loader has no such
 * neighbor at the first {@code toClass} call; the only pre-loaded classes are inherited
 * via parent delegation and so live in the parent's loader, not the rule loader.
 *
 * <p>This contract sidesteps the issue entirely: a class loader that implements it
 * publishes a {@code defineClass} method as part of its API, and the runtime-rule
 * generator path calls it directly with {@code CtClass.toBytecode()} bytes — no
 * reflection, no deprecated overload, no {@code --add-opens} requirement on the OAP
 * JVM. Production loaders (the static path) keep working through their existing
 * {@code toClass(Class<?>)} neighbor-based path.
 *
 * <p>The interface is intentionally minimal — a single bytecode-defining method that
 * mirrors what {@link ClassLoader#defineClass(String, byte[], int, int)} does. Lifecycle
 * (parent delegation, URL search) stays the implementor's concern.
 */
public interface BytecodeClassDefiner {

    /**
     * Define {@code bytecode} as a {@link Class} in this loader's namespace.
     *
     * @param className   fully-qualified binary name, must match the class's
     *                    {@code this_class} attribute in the bytecode.
     * @param bytecode    a complete classfile (e.g., from {@code CtClass.toBytecode()}).
     * @return the resolved {@link Class} object loaded by this defining loader.
     */
    Class<?> defineClass(String className, byte[] bytecode);
}
