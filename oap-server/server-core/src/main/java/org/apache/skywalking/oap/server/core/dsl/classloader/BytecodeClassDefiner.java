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

package org.apache.skywalking.oap.server.core.dsl.classloader;

import java.io.IOException;
import javassist.CannotCompileException;
import javassist.CtClass;

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

    /**
     * Loads a generated class, choosing the definition path from the target loader.
     *
     * <p>Three call sites did this identically — MAL's and LAL's generators and
     * {@code MeterSystem} — so a change had to be made three times and the reasoning above was
     * restated in four places. It lives on this interface rather than in a class of its own
     * because the whole decision is about whether the loader implements this interface.
     *
     * @param ctClass           the generated class, not yet loaded
     * @param targetClassLoader the loader to define into; null selects the neighbour form, which
     *                          puts the class in {@code packageAnchor}'s package and loader
     * @param packageAnchor     a class naming the package to define into when there is no target
     *                          loader — each DSL keeps its own empty holder for this
     * @return the loaded class
     * @throws CannotCompileException if Javassist cannot define it, or if serialising its bytes
     *                                fails
     */
    static Class<?> define(final CtClass ctClass,
                           final ClassLoader targetClassLoader,
                           final Class<?> packageAnchor) throws CannotCompileException {
        if (targetClassLoader == null) {
            return ctClass.toClass(packageAnchor);
        }
        if (targetClassLoader instanceof BytecodeClassDefiner) {
            try {
                return ((BytecodeClassDefiner) targetClassLoader)
                    .defineClass(ctClass.getName(), ctClass.toBytecode());
            } catch (final IOException e) {
                // Reported as a compile failure rather than propagated: every caller already
                // handles CannotCompileException, and failing to serialise bytes this process
                // just generated is not meaningfully different from a compile failure.
                throw new CannotCompileException(
                    "failed to serialise " + ctClass.getName() + " bytes", e);
            }
        }
        return ctClass.toClass(targetClassLoader, null);
    }
}
