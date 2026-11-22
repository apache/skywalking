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

/**
 * The static type the compiler infers for an expression, driving arithmetic promotion, comparison widening and
 * cast emission. Each numeric variant carries its Java keyword, its wrapper class name, its primitive
 * {@link Class} and the conversion that turns an arbitrary object into that primitive, see {@link TALRuntimeRoles};
 * each type an {@code as} names, its boxed class.
 */
public enum ExprType {
    INT("int", "Integer", int.class, Integer.class, TALRuntimeRoles.method("toInt")),
    LONG("long", "Long", long.class, Long.class, TALRuntimeRoles.method("toLong")),
    FLOAT("float", "Float", float.class, Float.class, TALRuntimeRoles.method("toFloat")),
    DOUBLE("double", "Double", double.class, Double.class, TALRuntimeRoles.method("toDouble")),
    STRING(null, null, null, String.class, TALRuntimeRoles.method("toStr")),
    BOOLEAN(null, null, null, Boolean.class, TALRuntimeRoles.method("toBool")),
    OBJECT(null, null, null, null, null),
    UNKNOWN(null, null, null, null, null);

    public final String primitiveKeyword;
    public final String wrapperName;
    public final Class<?> primitiveClass;
    /**
     * The class an {@code as} of this type converts to, null for a type no {@code as} names.
     */
    public final Class<?> boxedClass;
    public final String runtimeHelper;

    ExprType(final String primitiveKeyword, final String wrapperName, final Class<?> primitiveClass,
             final Class<?> boxedClass, final String runtimeHelper) {
        this.primitiveKeyword = primitiveKeyword;
        this.wrapperName = wrapperName;
        this.primitiveClass = primitiveClass;
        this.boxedClass = boxedClass;
        this.runtimeHelper = runtimeHelper;
    }

    public boolean isNumeric() {
        return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    /**
     * @param cast the type an {@code as} names, or null
     * @return the type of {@code as String}, {@code as Long}, {@code as Integer}, {@code as Double}, {@code as Float}
     *     and {@code as Boolean}; {@link #UNKNOWN} for any other
     */
    public static ExprType ofCast(final String cast) {
        for (final ExprType type : values()) {
            if (type.boxedClass != null && type.boxedClass.getSimpleName().equals(cast)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * @return whether an {@code as} names a number or {@code Boolean}, which converts the value and keeps a null
     */
    public static boolean isScalarCast(final String cast) {
        final ExprType type = ofCast(cast);
        return type.isNumeric() || type == BOOLEAN;
    }
}
