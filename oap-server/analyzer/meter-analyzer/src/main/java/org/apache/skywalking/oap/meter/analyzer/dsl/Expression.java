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

import com.google.common.collect.ImmutableMap;
import groovy.lang.GroovyObjectSupport;
import groovy.util.DelegatingScript;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Expression {

    private final DelegatingScript expression;

    Expression(DelegatingScript expression) {
        this.expression = expression;
    }

    public Result run(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        expression.setDelegate(new GroovyObjectSupport() {

            public SampleFamily propertyMissing(String metricName) {
                if (sampleFamilies.containsKey(metricName)) {
                    return sampleFamilies.get(metricName);
                }
                if (log.isDebugEnabled()) {
                    log.debug("{} doesn't exist in {}", metricName, sampleFamilies.keySet());
                }
                throw new IllegalArgumentException("[" + metricName + "] can't be found");
            }

            @Override public Object invokeMethod(String name, Object args) {
                //TODO: Validate the name is one of meter functions
                return super.invokeMethod(name, args);
            }

        });
        try {
            SampleFamily sf = (SampleFamily) expression.run();
            return Result.success(sf);
        } catch (Throwable t) {
            return Result.fail(t);
        }
    }

}
