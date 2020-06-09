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

package org.apache.skywalking.e2e.dashboard;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DashboardConfigurationsMatcher extends AbstractMatcher<DashboardConfigurations> {
    private List<DashboardConfigurationMatcher> configurations;

    @Override
    public void verify(final DashboardConfigurations configurations) {
        DashboardConfigurationMatcher matcher = this.configurations.get(0);
        for (int i = 0; i < configurations.size(); i++) {
            DashboardConfiguration configuration = configurations.getConfigurations().get(i);
            if (matcher.getName().equals(configuration.getName())) {
                matcher.verify(configuration);
                return;
            }
        }
        throw new RuntimeException("Assertion failed!");
    }
}