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

import java.util.concurrent.TimeUnit;

/**
 * Help to build the {@link SampleFamily}.
 */
public class SampleFamilyBuilder {
    private final Sample[] samples;
    private final SampleFamily.RunningContext context;

    SampleFamilyBuilder(Sample[] samples, SampleFamily.RunningContext context) {
        this.samples = samples;
        this.context = context;
    }

    public static SampleFamilyBuilder newBuilder(Sample... samples) {
        return new SampleFamilyBuilder(samples, SampleFamily.RunningContext.instance());
    }

    public SampleFamilyBuilder histogramType(HistogramType type) {
        this.context.setHistogramType(type);
        return this;
    }

    public SampleFamilyBuilder defaultHistogramBucketUnit(TimeUnit unit) {
        this.context.setDefaultHistogramBucketUnit(unit);
        return this;
    }

    /**
     * Build Sample Family
     */
    public SampleFamily build() {
        return SampleFamily.build(this.context, this.samples);
    }

}
