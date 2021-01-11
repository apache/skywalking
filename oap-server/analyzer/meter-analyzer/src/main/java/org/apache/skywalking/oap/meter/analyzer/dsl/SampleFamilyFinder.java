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
import groovy.lang.GroovyInterceptable;
import groovy.lang.MetaClass;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * Help to find {@link SampleFamily}, support "." in metric name
 */
@Slf4j
public class SampleFamilyFinder extends SampleFamily implements GroovyInterceptable {
    private String metricNameAppender;
    private final String literal;
    private final ImmutableMap<String, SampleFamily> sampleFamilies;

    SampleFamilyFinder(String currentMetricName, String literal, ImmutableMap<String, SampleFamily> sampleFamilies) {
        super(null, null);
        metricNameAppender = currentMetricName;
        this.literal = literal;
        this.sampleFamilies = sampleFamilies;
    }

    /**
     * Trying to append metrics name and find it
     */
    public SampleFamily propertyMissing(String propertyName) {
        metricNameAppender += "." + propertyName;

        // Find in repository
        SampleFamily sampleFamily = sampleFamilies.get(metricNameAppender);
        if (sampleFamily != null) {
            // Add sample family name
            ExpressionParsingContext.get().ifPresent(ctx -> ctx.samples.add(metricNameAppender));
            return sampleFamily;
        }

        // Keep finding
        return this;
    }

    @Override
    public MetaClass getMetaClass() {
        return InvokerHelper.getMetaClass(this.getClass());
    }

    @Override
    public void setMetaClass(MetaClass metaClass) {
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.samples.add(metricNameAppender));
        if (!ExpressionParsingContext.get().isPresent()) {
            log.warn("{} referred by \"{}\" doesn't exist in {}", metricNameAppender, literal, sampleFamilies.keySet());
        }
        // Also could find sample family
        return this.getMetaClass().invokeMethod(SampleFamily.EMPTY, name, args);
    }
}
