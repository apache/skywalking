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

package org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry;

public enum K8sRetagType implements Retag {
    Pod2Service {
        @Override
        public Sample[] execute(final Sample[] ss,
                                final String newLabelName,
                                final String existingLabelName,
                                final String namespaceLabelName) {
            Sample[] samples = Arrays.stream(ss).map(sample -> {
                String podName = sample.getLabels().get(existingLabelName);
                String namespace = sample.getLabels().get(namespaceLabelName);
                if (!Strings.isNullOrEmpty(podName) && !Strings.isNullOrEmpty(namespace)) {
                    String serviceName = K8sInfoRegistry.getInstance().findServiceName(namespace, podName);
                    if (Strings.isNullOrEmpty(serviceName)) {
                        serviceName = BLANK;
                    }
                    Map<String, String> labels = Maps.newHashMap(sample.getLabels());
                    labels.put(newLabelName, serviceName);
                    return sample.toBuilder().labels(ImmutableMap.copyOf(labels)).build();
                }
                return sample;
            }).toArray(Sample[]::new);
            return samples;
        }
    }
}
