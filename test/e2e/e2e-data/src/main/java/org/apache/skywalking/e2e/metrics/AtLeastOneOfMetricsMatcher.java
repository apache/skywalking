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

package org.apache.skywalking.e2e.metrics;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;
import org.assertj.core.api.Condition;

import static org.assertj.core.api.Assertions.assertThat;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class AtLeastOneOfMetricsMatcher extends AbstractMatcher<Metrics> {
    private MetricsValueMatcher value;

    @Override
    public void verify(Metrics metrics) {
        assertThat(metrics.getValues()).isNotEmpty();
        assertThat(metrics.getValues()).areAtLeastOne(new Condition<MetricsValue>() {
            @Override
            public boolean matches(MetricsValue value) {
                try {
                    AtLeastOneOfMetricsMatcher.this.getValue().verify(value);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }
        });
    }
}
