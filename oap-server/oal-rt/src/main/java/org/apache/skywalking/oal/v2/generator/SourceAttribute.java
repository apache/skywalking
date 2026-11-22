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

package org.apache.skywalking.oal.v2.generator;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.oal.v2.util.ClassMethodUtil;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;

/**
 * An attribute path of a source class, such as {@code protocol.http.latency} or {@code tag["key"]}, as the getter
 * chain the generated code calls, which Javassist resolves, and the type it returns when that is plain to see.
 */
@Getter
final class SourceAttribute {
    private final String path;
    /**
     * The getter chain after {@code source.}, such as {@code getProtocol().getHttp().getLatency()}.
     */
    private final String accessor;
    /**
     * The type the chain returns, or null when it is not plain to see: past a keyed getter, whose overloads Javassist
     * may resolve otherwise than reflection, past a getter that is not public, as a missing one, which Javassist
     * reports, or past a class whose methods reflection cannot read, as for a missing optional dependency.
     */
    private final Class<?> type;

    private SourceAttribute(String path, String accessor, Class<?> type) {
        this.path = path;
        this.accessor = accessor;
        this.type = type;
    }

    /**
     * @param booleanRead the last segment is read as a boolean, through {@code isX()} rather than {@code getX()}.
     */
    static SourceAttribute resolve(Class<?> sourceClass, String path, boolean booleanRead) {
        final List<String> segments = ClassMethodUtil.splitPath(path);
        final StringBuilder accessor = new StringBuilder();
        Class<?> owner = sourceClass;
        // the type each step reaches, for the list: past a keyed getter too, by the overload its String key calls;
        // null once a step's type cannot be told
        Class<?> checked = sourceClass;
        // a getter the class lacks is left to Javassist, whose compile then fails naming it; a class reflection cannot
        // read (a missing optional dependency) too, where the path ends there
        boolean leftToJavassist = false;
        for (int i = 0; i < segments.size(); i++) {
            final String segment = segments.get(i);
            if (accessor.length() > 0) {
                accessor.append('.');
            }
            final int bracket = segment.indexOf('[');
            final boolean keyed = bracket > 0 && segment.endsWith("]");
            final String getter = keyed ? ClassMethodUtil.toGetMethod(segment.substring(0, bracket))
                : i == segments.size() - 1 && booleanRead
                ? ClassMethodUtil.toIsMethod(segment) : ClassMethodUtil.toGetMethod(segment);
            if ("getClass".equals(getter)) {
                // no class's attribute, whether or not reflection can read the class
                throw new IllegalArgumentException("The attribute path " + describe(sourceClass, path)
                    + " reads getClass(), which is no attribute (see the OAL documentation)");
            }
            if (!leftToJavassist) {
                if (checked == null) {
                    throw new IllegalArgumentException("The attribute path " + describe(sourceClass, path)
                        + " reads " + getter + "() past a value whose type is not plain to see; a path reads the "
                        + "source's attributes and their nested ones (see the OAL documentation)");
                }
                try {
                    if (!DslMethods.has(checked, getter, keyed ? 1 : 0)) {
                        leftToJavassist = true;
                    } else {
                        requireReadable(sourceClass, path, checked, getter, keyed ? 1 : 0);
                        checked = keyed ? keyedReturnType(checked, getter) : returnType(checked, getter);
                    }
                } catch (LinkageError unreadable) {
                    if (i < segments.size() - 1) {
                        throw new IllegalArgumentException("The attribute path " + describe(sourceClass, path)
                            + " reads past " + checked.getName() + ", whose methods cannot be read here ("
                            + unreadable + ")");
                    }
                    leftToJavassist = true;
                }
            }
            if (keyed) {
                final String key = TypeCastUtil.decode(segment.substring(bracket + 1, segment.length() - 1));
                accessor.append(getter).append('(').append(TypeCastUtil.toJavaSource(key)).append(')');
                owner = null;
            } else {
                accessor.append(getter).append("()");
                owner = owner == null ? null : returnType(owner, getter);
            }
        }
        return new SourceAttribute(path, accessor.toString(), owner);
    }

    /**
     * The path as a matcher registered in place of a stock one is called with it: split at each dot, a keyed read with
     * its key as written, and its type read from public getters, none for a keyed read. The path is checked as
     * {@link #resolve} checks it.
     */
    static SourceAttribute asWritten(Class<?> sourceClass, String path, boolean booleanRead) {
        resolve(sourceClass, path, booleanRead);
        return new SourceAttribute(path, writtenAccessor(path, booleanRead), writtenType(sourceClass, path));
    }

    /**
     * The keyed attributes, such as {@code tag["k"]}, a filter or a function argument reads, the ones a path split at
     * each dot reads ({@link #asWritten}): a filter's whole attribute, as {@code tag["http.method"]}, or a key in a
     * dotted path, as {@code origin.label["region"]}, holding no dot and not read as a boolean; a function argument the
     * latter only. Any other keyed form is rejected rather than taken with no check of what it reads.
     */
    static void checkKeyedPath(String path, boolean filter, boolean booleanRead, String what) {
        final List<String> segments = ClassMethodUtil.splitPath(path);
        boolean keyed = false;
        boolean dottedKey = false;
        for (String segment : segments) {
            final int bracket = segment.indexOf('[');
            if (bracket > 0 && segment.endsWith("]")) {
                keyed = true;
                dottedKey |= segment.indexOf('.', bracket) > 0;
            }
        }
        if (!keyed) {
            return;
        }
        final boolean dotted = segments.size() > 1 && !dottedKey && !booleanRead;
        final boolean accepted = filter ? segments.size() == 1 || dotted && !path.endsWith("]") : dotted;
        if (!accepted) {
            throw new IllegalArgumentException(what + ": the keyed attribute " + path + " is not supported here: " + (filter
                ? "a filter reads one as its whole attribute, such as tag[\"http.method\"], or before the last segment of "
                    + "a dotted path, such as entries[\"k\"].name, with a key holding no dot and not as a boolean"
                : "a function argument reads one only in a dotted path, such as origin.label[\"region\"], with a key "
                    + "holding no dot and not as a boolean"));
        }
    }

    /**
     * A path reads the source's attributes and their nested ones, never a JDK value's ({@code name.bytes}).
     */
    private static void requireReadable(Class<?> sourceClass, String path, Class<?> owner, String getter,
                                        int arity) {
        if (!OALCallables.get().allows(owner, getter, arity)) {
            throw new IllegalArgumentException("The attribute path " + describe(sourceClass, path) + " reads "
                + owner.getSimpleName() + "." + getter + "(), which is no attribute: a path reads the source's "
                + "attributes and their nested ones (see the OAL documentation)");
        }
    }

    /**
     * @return what the keyed getter returns for the String key the path gives it, as Javassist picks the overload
     */
    private static Class<?> keyedReturnType(Class<?> owner, String getter) {
        final Method called = DslMethods.overload(owner, getter, new Class<?>[] {String.class});
        return called == null ? null : called.getReturnType();
    }

    private static Class<?> returnType(Class<?> owner, String name) {
        try {
            final Method method = DslMethods.find(owner, name);
            return method.getReturnType();
        } catch (NoSuchMethodException | LinkageError | SecurityException e) {
            return null;
        }
    }

    private static String writtenAccessor(String path, boolean booleanRead) {
        final int bracket = path.indexOf('[');
        if (bracket > 0 && path.endsWith("]")) {
            return ClassMethodUtil.toGetMethod(path.substring(0, bracket))
                + "(" + path.substring(bracket + 1, path.length() - 1) + ")";
        }
        if (path.contains(".")) {
            final List<String> attributes = Arrays.asList(path.split("\\."));
            return booleanRead ? ClassMethodUtil.toIsMethod(attributes) : ClassMethodUtil.toGetMethod(attributes);
        }
        return (booleanRead ? ClassMethodUtil.toIsMethod(path) : ClassMethodUtil.toGetMethod(path)) + "()";
    }

    private static Class<?> writtenType(Class<?> sourceClass, String path) {
        if (path.indexOf('[') > 0 && path.endsWith("]")) {
            return null;
        }
        try {
            Class<?> type = sourceClass;
            for (String attribute : path.split("\\.")) {
                Method accessor;
                try {
                    accessor = type.getMethod(ClassMethodUtil.toGetMethod(attribute));
                } catch (NoSuchMethodException e) {
                    accessor = type.getMethod(ClassMethodUtil.toIsMethod(attribute));
                }
                type = accessor.getReturnType();
            }
            return type;
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    static String describe(Class<?> sourceClass, String path) {
        return sourceClass.getSimpleName() + "." + path;
    }
}
