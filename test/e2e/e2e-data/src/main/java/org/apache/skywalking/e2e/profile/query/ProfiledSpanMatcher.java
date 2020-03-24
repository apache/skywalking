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

package org.apache.skywalking.e2e.profile.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProfiledSpanMatcher extends AbstractMatcher<ProfiledSpan> {
    private String spanId;
    private String parentSpanId;
    private String serviceCode;
    private String startTime;
    private String endTime;
    private String endpointName;
    private List<ProfiledSpanTagMatcher> tags;

    @Override
    public void verify(ProfiledSpan span) {
        doVerify(spanId, span.getSpanId());
        doVerify(parentSpanId, span.getParentSpanId());
        doVerify(serviceCode, span.getServiceCode());
        doVerify(startTime, span.getStartTime());
        doVerify(endTime, span.getEndTime());
        doVerify(endpointName, span.getEndpointName());

        assertThat(tags).hasSameSizeAs(span.getTags());

        tags.sort(Comparator.comparing(ProfiledSpanTagMatcher::getKey));
        span.getTags().sort(Comparator.comparing(ProfiledSpanTag::getKey));

        for (int i = 0; i < tags.size(); i++) {
            tags.get(i).verify(span.getTags().get(i));
        }
    }
}
