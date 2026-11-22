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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

/**
 * Small helpers the generated Java source needs.
 */
public final class TALCodegenHelper {
    private TALCodegenHelper() {
    }

    /**
     * @param primitiveType a primitive class
     * @return the wrapper type's simple name, or null when the argument is not one of the boxable primitives
     */
    public static String boxTypeName(final Class<?> primitiveType) {
        if (primitiveType == int.class) {
            return "Integer";
        } else if (primitiveType == long.class) {
            return "Long";
        } else if (primitiveType == boolean.class) {
            return "Boolean";
        } else if (primitiveType == double.class) {
            return "Double";
        } else if (primitiveType == float.class) {
            return "Float";
        } else if (primitiveType == short.class) {
            return "Short";
        } else if (primitiveType == byte.class) {
            return "Byte";
        } else if (primitiveType == char.class) {
            return "Character";
        }
        return null;
    }

    /**
     * Whether a Java cast from one reference type to another can hold for some value, as javac decides it: one is
     * the other's subtype or supertype, or an interface meets a class that is not final.
     *
     * @param from the value's static type, null when unknown
     * @param to   the type cast to
     * @return false when no value of {@code from} can be a {@code to}
     */
    public static boolean castable(final Class<?> from, final Class<?> to) {
        if (from == null) {
            return true;
        }
        if (from.isArray() && to.isArray()) {
            final Class<?> fromElement = from.getComponentType();
            final Class<?> toElement = to.getComponentType();
            // A byte[] is never a char[]: arrays of primitives cast only to their own type.
            return fromElement.isPrimitive() || toElement.isPrimitive() ? fromElement == toElement
                : castable(fromElement, toElement);
        }
        if (from.isPrimitive() || to.isPrimitive()) {
            return true;
        }
        if (to.isAssignableFrom(from) || from.isAssignableFrom(to)) {
            return true;
        }
        if (from.isArray() || to.isArray()) {
            return false;
        }
        return from.isInterface() && (to.isInterface() || !Modifier.isFinal(to.getModifiers()))
            || to.isInterface() && !Modifier.isFinal(from.getModifiers());
    }

    /**
     * @return the local-variable descriptor of a reference type, {@code [B} for a byte array and
     * {@code Ljava/lang/String;} for a class
     */
    public static String descriptor(final Class<?> type) {
        return type.isArray() ? type.getName().replace('.', '/') : "L" + type.getName().replace('.', '/') + ";";
    }

    /**
     * @return the type as Java source names it, {@code byte[]} for an array rather than its binary name
     */
    public static String javaTypeName(final Class<?> type) {
        return type.isArray() ? javaTypeName(type.getComponentType()) + "[]" : type.getName();
    }

    /**
     * The accessor a chain segment names, tried as {@code getX()}, then {@code isX()}, then {@code x()} itself, so
     * a boolean view method such as {@code isEntry()} and a field-named one such as {@code isError()} both resolve.
     *
     * @param type  the type the segment is read on
     * @param field the segment's name as the rule writes it
     * @return the no-argument method, or null when the type has none of the three
     */
    public static Method resolveGetter(final Class<?> type, final String field) {
        final String capitalized = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (final String name : List.of("get" + capitalized, "is" + capitalized, field)) {
            try {
                return DslMethods.find(type, name);
            } catch (NoSuchMethodException ignored) {
                // try the next spelling
            }
        }
        return null;
    }

    /**
     * @return the code of {@code receiver.toString()}, the call written out or read as a property, when the receiver's
     *     static type can hold an array, a collection or a map, which a rule prints by content
     *     ({@code DslStrings#text}), through the null-safe form under {@code ?.}; null for any other call
     */
    public static String printedByContent(final Class<?> receiverType, final Method method, final String receiver,
                                          final boolean safeNav) {
        if (method == null || !"toString".equals(method.getName()) || method.getParameterCount() != 0
            || !DslStrings.mayHoldItems(receiverType)) {
            return null;
        }
        return TALRuntimeRoles.method(safeNav ? "toString" : "text") + "(" + receiver + ")";
    }

    /**
     * @param genericType a method's generic return type
     * @return the element class when it is a {@code List<T>} or {@code List<? extends T>}, else null
     */
    public static Class<?> listElementType(final Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            return null;
        }
        final ParameterizedType parameterized = (ParameterizedType) genericType;
        if (!(parameterized.getRawType() instanceof Class)
            || !List.class.isAssignableFrom((Class<?>) parameterized.getRawType())) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (argument instanceof WildcardType) {
            argument = ((WildcardType) argument).getUpperBounds()[0];
        }
        if (argument instanceof ParameterizedType) {
            argument = ((ParameterizedType) argument).getRawType();
        }
        return argument instanceof Class ? (Class<?>) argument : null;
    }
}
