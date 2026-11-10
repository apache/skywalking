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
 * {@link Class} and the conversion that turns an arbitrary object into that primitive, see {@link TALRuntimeRoles}.
 */
public enum ExprType {
    INT("int", "Integer", int.class, TALRuntimeRoles.method("toInt")),
    LONG("long", "Long", long.class, TALRuntimeRoles.method("toLong")),
    FLOAT("float", "Float", float.class, TALRuntimeRoles.method("toFloat")),
    DOUBLE("double", "Double", double.class, TALRuntimeRoles.method("toDouble")),
    STRING(null, null, null, TALRuntimeRoles.method("toStr")),
    BOOLEAN(null, null, null, TALRuntimeRoles.method("toBool")),
    OBJECT(null, null, null, null),
    UNKNOWN(null, null, null, null);

    public final String primitiveKeyword;
    public final String wrapperName;
    public final Class<?> primitiveClass;
    public final String runtimeHelper;

    ExprType(final String primitiveKeyword, final String wrapperName,
             final Class<?> primitiveClass, final String runtimeHelper) {
        this.primitiveKeyword = primitiveKeyword;
        this.wrapperName = wrapperName;
        this.primitiveClass = primitiveClass;
        this.runtimeHelper = runtimeHelper;
    }

    public boolean isNumeric() {
        return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }
}
