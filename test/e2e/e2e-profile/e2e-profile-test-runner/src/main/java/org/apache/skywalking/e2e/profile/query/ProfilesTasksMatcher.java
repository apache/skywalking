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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.e2e.verification.AbstractMatcher;
import org.assertj.core.api.Assertions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author MrPro
 */
@Setter
@Getter
public class ProfilesTasksMatcher extends AbstractMatcher<ProfileTasks> {

    private List<ProfileTaskMatcher> tasks;

    @Override
    public void verify(ProfileTasks matcher) {
        Assertions.assertThat(matcher.getTasks()).hasSameSizeAs(this.tasks);

        int size = this.getTasks().size();

        for (int i = 0; i < size; i++) {
            this.getTasks().get(i).verify(matcher.getTasks().get(i));
        }
    }
}
