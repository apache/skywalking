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

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProfileStackElementMatcher extends AbstractMatcher<ProfileAnalyzation.ProfileStackElement> {
    private String id;
    private String parentId;
    private String codeSignature;
    private String duration;
    private String durationChildExcluded;
    private String count;

    @Override
    public void verify(ProfileAnalyzation.ProfileStackElement element) {
        doVerify(id, element.getId());
        doVerify(parentId, element.getParentId());
        doVerify(codeSignature, element.getCodeSignature());
        doVerify(duration, element.getDuration());
        doVerify(durationChildExcluded, element.getDurationChildExcluded());
        doVerify(count, element.getCount());
    }
}