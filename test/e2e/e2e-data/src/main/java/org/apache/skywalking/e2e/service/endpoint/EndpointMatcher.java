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

package org.apache.skywalking.e2e.service.endpoint;

import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EndpointMatcher extends AbstractMatcher<Endpoint> {
    private String key;
    private String label;

    @Override
    public void verify(final Endpoint endpoint) {
        if (Objects.equals("/health", endpoint.getKey())) {
            return;
        }
        if (Objects.nonNull(getKey())) {
            doVerify(getKey(), endpoint.getKey());
        }
        if (Objects.nonNull(getLabel())) {
            doVerify(getLabel(), endpoint.getLabel());
        }
    }
}
