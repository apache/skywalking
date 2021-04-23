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

import lombok.Data;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
public class DashboardConfigurationMatcher extends AbstractMatcher<DashboardConfiguration> {
    private String name;
    private String type;
    private String configuration;
    private String activated;
    private String disabled;

    @Override
    public void verify(final DashboardConfiguration configuration) {
        doVerify(this.name, configuration.getName());
        doVerify(this.type, String.valueOf(configuration.getType()));
        doVerify(this.configuration, configuration.getConfiguration());
        doVerify(this.activated, configuration.isActivated());
        doVerify(this.disabled, configuration.isDisabled());
    }
}
