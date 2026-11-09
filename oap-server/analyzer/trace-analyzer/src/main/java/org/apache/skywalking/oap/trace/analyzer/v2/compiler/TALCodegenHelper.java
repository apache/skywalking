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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

/**
 * Small helpers the generated Java source needs, shared by every DSL's codegen.
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
     * @return the wrapper class of a primitive type, or the type itself when it is not primitive
     */
    public static Class<?> boxedClass(final Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else if (type == double.class) {
            return Double.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == char.class) {
            return Character.class;
        }
        return type;
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
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try the next spelling
            }
        }
        return null;
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
