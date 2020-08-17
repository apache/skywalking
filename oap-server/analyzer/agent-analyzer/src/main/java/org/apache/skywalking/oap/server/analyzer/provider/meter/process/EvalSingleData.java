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

import lombok.Data;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Support counter, gauge
 */
@Data
public class EvalSingleData extends EvalData<EvalData> {

    private double value;

    public static EvalSingleData build(MeterSingleValue value, MeterProcessor processor) {
        final EvalSingleData singleEvalData = new EvalSingleData();
        singleEvalData.name = value.getName();
        singleEvalData.labels = value.getLabelsList().stream()
            .collect(Collectors.toMap(Label::getName, Label::getValue));
        singleEvalData.processor = processor;
        singleEvalData.value = value.getValue();
        return singleEvalData;
    }

    @Override
    public EvalData multiply(double value) {
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value * value);
    }

    @Override
    public EvalData multiply(EvalData data) {
        if (!(data instanceof EvalSingleData)) {
            throw new IllegalArgumentException("Only support multiply from single value");
        }
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value * ((EvalSingleData) data).value);
    }

    @Override
    public EvalData add(double value) {
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value + value);
    }

    @Override
    public EvalData scale(Integer value) {
        return copyTo(EvalSingleData.class, instance ->
            instance.value = BigDecimal.valueOf(this.value).multiply(BigDecimal.TEN.pow(value)).doubleValue());
    }

    @Override
    public EvalData add(EvalData data) {
        if (!(data instanceof EvalSingleData)) {
            throw new IllegalArgumentException("Only support add from single value");
        }
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value + ((EvalSingleData) data).value);
    }

    @Override
    public EvalData minus(double value) {
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value - value);
    }

    @Override
    public EvalData minus(EvalData data) {
        if (!(data instanceof EvalSingleData)) {
            throw new IllegalArgumentException("Only support reduce from single value");
        }
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value - ((EvalSingleData) data).value);
    }

    @Override
    public EvalData divide(double value) {
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value / value);
    }

    @Override
    public EvalData divide(EvalData data) {
        if (!(data instanceof EvalSingleData)) {
            throw new IllegalArgumentException("Only support mean from single value");
        }
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value / ((EvalSingleData) data).value);
    }

    @Override
    public EvalData irate(String range) {
        return copyTo(EvalSingleData.class,
            instance -> instance.value = processor.window().get(this).apply(Window.CalculateType.IRATE, range));
    }

    @Override
    public EvalData rate(String range) {
        return copyTo(EvalSingleData.class,
            instance -> instance.value = processor.window().get(this).apply(Window.CalculateType.RATE, range));
    }

    @Override
    public EvalData increase(String range) {
        return copyTo(EvalSingleData.class,
            instance -> instance.value = processor.window().get(this).apply(Window.CalculateType.INCREASE, range));
    }

    @Override
    EvalData combine(EvalData data) {
        final EvalSingleData value = (EvalSingleData) data;
        return copyTo(EvalSingleData.class, instance -> instance.value = this.value + value.value);
    }
}
