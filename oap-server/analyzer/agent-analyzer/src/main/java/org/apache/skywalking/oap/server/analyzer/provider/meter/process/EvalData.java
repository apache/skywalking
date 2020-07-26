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

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base evaluable data, using on groovy process
 */
@Data
public abstract class EvalData<FROM extends EvalData> implements MeterEvalOperation<FROM> {

    /**
     * Meter name
     */
    protected String name;

    /**
     * Meter labels
     */
    protected Map<String, String> labels;

    /**
     * processor
     */
    protected MeterProcessor processor;

    /**
     * Check has the same tag
     */
    boolean hasSameTag(String tagKey, String tagValue) {
        return Objects.equals(tagValue, labels.get(tagKey));
    }

    /**
     * Combine two data
     */
    abstract EvalData combine(FROM data);

    /**
     * Copy to a new data, make it immutable
     */
    protected <T extends EvalData> T copyTo(Class<T> cls, Consumer<T> custom) {
        final T instance;
        try {
            instance = cls.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Copy data failed", e);
        }

        // Basic info
        instance.name = this.name;
        instance.labels = this.labels;
        instance.processor = this.processor;

        custom.accept(instance);
        return instance;
    }

}
