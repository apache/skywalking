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

import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple matcher to verify the given {@code Service} is expected
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InstanceMatcher extends AbstractMatcher<Instance> {

    private String key;
    private String label;
    private String instanceUUID;
    private List<AttributeMatcher> attributes;

    @Override
    public void verify(final Instance instance) {
        if (Objects.nonNull(getKey())) {
            verifyKey(instance);
        }

        if (Objects.nonNull(getLabel())) {
            verifyLabel(instance);
        }

        if (Objects.nonNull(getInstanceUUID())) {
            verifyInstanceUUID(instance);
        }

        if (Objects.nonNull(getAttributes())) {
            verifyAttributes(instance);
        }
    }

    private void verifyKey(Instance instance) {
        final String expected = this.getKey();
        final String actual = instance.getKey();

        doVerify(expected, actual);
    }

    private void verifyLabel(Instance instance) {
        final String expected = this.getLabel();
        final String actual = String.valueOf(instance.getLabel());

        doVerify(expected, actual);
    }

    private void verifyInstanceUUID(Instance instance) {
        final String expected = this.getInstanceUUID();
        final String actual = instance.getInstanceUUID();

        doVerify(expected, actual);
    }

    private void verifyAttributes(Instance instance) {
        final List<AttributeMatcher> expected = this.getAttributes();
        final List<Attribute> actual = instance.getAttributes();

        assertThat(actual).hasSameSizeAs(expected);

        int size = expected.size();

        for (int i = 0; i < size; i++) {
            expected.get(i).verify(actual.get(i));
        }
    }
}
