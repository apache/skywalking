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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import groovy.lang.Closure;
import io.vavr.Function2;

/**
 * NumberClosure intends to extend primitive Number to do binary operation with {@link SampleFamily}
 */
public class NumberClosure extends Closure<SampleFamily> {

    private final Function2<Number, SampleFamily, SampleFamily> fn;

    public NumberClosure(Object owner, Function2<Number, SampleFamily, SampleFamily> fn) {
        super(owner);
        this.fn = fn;
    }

    /**
     * Call binary operation, for instances, {@code 100 + server_cpu_seconds}. {@code server_cpu_seconds}'s type
     * should be {@link SampleFamily}
     *
     * @param arguments the right-hand side of binary operation, usually a {@link SampleFamily} object.
     * @return the result of binary operation.
     */
    @Override
    public SampleFamily call(Object arguments) {
        return fn.apply((Number) this.getDelegate(), (SampleFamily) arguments);
    }

    /**
     * getParameterTypes hints the right-hand side should be {@link SampleFamily}.
     *
     * @return the class array contains argument type.
     */
    @Override
    public Class[] getParameterTypes() {
        return new Class[] { SampleFamily.class};
    }
}

