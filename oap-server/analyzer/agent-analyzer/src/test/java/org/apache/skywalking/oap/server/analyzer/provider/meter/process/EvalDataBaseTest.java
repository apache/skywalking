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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

/**
 * Eval base test
 */
public class EvalDataBaseTest {

    private MeterProcessor processor;

    /**
     * Build histogram data
     */
    protected EvalHistogramData buildHistogram(String meterName, String[] labels, double... bucketAndValues) {
        final MeterHistogram.Builder histogramBuilder = MeterHistogram.newBuilder().setName(meterName);
        for (int i = 0; i < labels.length; i += 2) {
            histogramBuilder.addLabels(Label.newBuilder().setName(labels[i]).setValue(labels[i + 1]).build());
        }
        for (int i = 0; i < bucketAndValues.length; i += 2) {
            histogramBuilder.addValues(MeterBucketValue.newBuilder().setBucket(bucketAndValues[i]).setCount((long) bucketAndValues[i + 1]).build());
        }
        return EvalHistogramData.build(histogramBuilder.build(), getProcessor());
    }

    /**
     * Build single data
     */
    protected EvalSingleData buildSingle(String meterName, String[] labels, double value) {
        final MeterSingleValue.Builder singleValue = MeterSingleValue.newBuilder().setName(meterName).setValue(value);
        for (int i = 0; i < labels.length; i += 2) {
            singleValue.addLabels(Label.newBuilder().setName(labels[i]).setValue(labels[i + 1]).build());
        }
        return EvalSingleData.build(singleValue.build(), getProcessor());
    }

    /**
     * Get or creat the processor
     */
    private MeterProcessor getProcessor() {
        if (processor != null) {
            return processor;
        }
        processor = Mockito.spy(new MeterProcessor(null));
        when(processor.window()).thenReturn(Window.getWindow("service", "instance"));
        return processor;
    }

}
