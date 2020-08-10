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

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProfileStackTreeMatcher extends AbstractMatcher<ProfileAnalyzation.ProfileStackTree> {

    private List<ProfileStackElementMatcher> elements;

    @Override
    public void verify(ProfileAnalyzation.ProfileStackTree profileStackTree) {
        int size = this.elements.size();

        for (int i = 0; i < size; i++) {
            boolean hasVerified = false;
            for (int dataElement = 0; dataElement < profileStackTree.getElements().size(); dataElement++) {
                try {
                    elements.get(i).verify(profileStackTree.getElements().get(dataElement));
                    hasVerified = true;
                    break;
                } catch (Throwable e) {
                }
            }

            if (!hasVerified) {
                throw new IllegalStateException("Cannot found " + elements.get(i).getCodeSignature());
            }
        }
    }
}