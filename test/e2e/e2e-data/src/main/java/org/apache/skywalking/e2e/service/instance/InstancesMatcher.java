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

package org.apache.skywalking.e2e.service.instance;

import java.util.LinkedList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;
import org.assertj.core.api.Assertions;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InstancesMatcher extends AbstractMatcher<Instances> {
    private List<InstanceMatcher> instances;

    public InstancesMatcher() {
        this.instances = new LinkedList<>();
    }

    @Override
    public void verify(final Instances instances) {
        Assertions.assertThat(instances.getInstances()).hasSameSizeAs(this.getInstances());

        int size = this.getInstances().size();

        for (int i = 0; i < size; i++) {
            this.getInstances().get(i).verify(instances.getInstances().get(i));
        }
    }
}
