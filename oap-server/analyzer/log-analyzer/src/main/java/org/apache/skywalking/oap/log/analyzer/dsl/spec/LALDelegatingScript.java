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

package org.apache.skywalking.oap.log.analyzer.dsl.spec;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.util.DelegatingScript;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.filter.FilterSpec;

public class LALDelegatingScript extends DelegatingScript {
    @Override
    public Object run() {
        return null;
    }

    public void filter(@DelegatesTo(value = FilterSpec.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        closure.setDelegate(getDelegate());
        closure.call();
    }

    public void json(@DelegatesTo(value = FilterSpec.class) Closure<?> closure) {
        closure.setDelegate(getDelegate());
        closure.call();
    }

    public void text(@DelegatesTo(value = FilterSpec.class) Closure<?> closure) {
        closure.setDelegate(getDelegate());
        closure.call();
    }

    public void extractor(@DelegatesTo(value = FilterSpec.class) Closure<?> closure) {
        closure.setDelegate(getDelegate());
        closure.call();
    }

    public void sink(@DelegatesTo(value = FilterSpec.class) Closure<?> closure) {
        closure.setDelegate(getDelegate());
        closure.call();
    }
}
