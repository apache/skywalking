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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a closure body has declared at a point of it: each local's type, the locals declared with a primitive type, the
 * def declaration each name refers to, and the locals holding a map the closure built. Each branch of an if declares
 * in its own scope, as Java's: {@link #snapshot()} before it, {@link #restore} after.
 */
final class ClosureScope {
    private final Map<String, Class<?>> locals = new HashMap<>();
    /** Locals declared with a primitive type (int n): Groovy compares and computes them as primitives. */
    private final Set<String> staticLocals = new HashSet<>();
    /** The def declaration each name currently refers to. */
    private final Map<String, MALExpressionModel.ClosureVarDecl> defLocals = new HashMap<>();
    /**
     * Locals declared with a map literal and never assigned another value: the only maps besides the labels a closure
     * writes (a local holding the labels, or one that may, reads them only).
     */
    private final Set<String> builtMaps = new HashSet<>();

    void clear() {
        locals.clear();
        staticLocals.clear();
        defLocals.clear();
        builtMaps.clear();
    }

    /**
     * @param type the local's type, null where not known
     */
    void declare(final String name, final Class<?> type) {
        locals.put(name, type);
        staticLocals.remove(name);
    }

    /**
     * A local declared with a primitive type: Groovy compiles its comparisons as primitive ones.
     */
    void declareStatic(final String name) {
        staticLocals.add(name);
    }

    boolean isDeclared(final String name) {
        return locals.containsKey(name);
    }

    Class<?> localType(final String name) {
        return locals.get(name);
    }

    boolean isStatic(final String name) {
        return staticLocals.contains(name);
    }

    /**
     * @param def the def declaration the name now refers to, null for a declaration of a type
     */
    void setDef(final String name, final MALExpressionModel.ClosureVarDecl def) {
        if (def == null) {
            defLocals.remove(name);
        } else {
            defLocals.put(name, def);
        }
    }

    MALExpressionModel.ClosureVarDecl defOf(final String name) {
        return defLocals.get(name);
    }

    /**
     * @param built whether the local now holds a map the closure built
     */
    void setBuiltMap(final String name, final boolean built) {
        if (built) {
            builtMaps.add(name);
        } else {
            builtMaps.remove(name);
        }
    }

    boolean isBuiltMap(final String name) {
        return builtMaps.contains(name);
    }

    /**
     * @return the scope as it stands, to restore when a branch ends
     */
    Snapshot snapshot() {
        return new Snapshot(this);
    }

    /**
     * Back to the scope a snapshot took: what was declared since is gone.
     */
    void restore(final Snapshot snapshot) {
        locals.clear();
        locals.putAll(snapshot.locals);
        staticLocals.clear();
        staticLocals.addAll(snapshot.staticLocals);
        defLocals.clear();
        defLocals.putAll(snapshot.defLocals);
        builtMaps.clear();
        builtMaps.addAll(snapshot.builtMaps);
    }

    /**
     * After the branches of an if: back to the scope before them, where a local holds a map the closure built only if
     * it did before and after each branch.
     *
     * @param before    the scope before the branches
     * @param afterThen the scope the first branch ended with; the second is the current one
     */
    void endBranches(final Snapshot before, final Snapshot afterThen) {
        final Set<String> built = new HashSet<>(builtMaps);
        built.retainAll(afterThen.builtMaps);
        built.retainAll(before.builtMaps);
        restore(before);
        builtMaps.retainAll(built);
    }

    /**
     * The scope at one point of a closure body.
     */
    static final class Snapshot {
        private final Map<String, Class<?>> locals;
        private final Set<String> staticLocals;
        private final Map<String, MALExpressionModel.ClosureVarDecl> defLocals;
        private final Set<String> builtMaps;

        private Snapshot(final ClosureScope scope) {
            this.locals = new HashMap<>(scope.locals);
            this.staticLocals = new HashSet<>(scope.staticLocals);
            this.defLocals = new HashMap<>(scope.defLocals);
            this.builtMaps = new HashSet<>(scope.builtMaps);
        }
    }
}
