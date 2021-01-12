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

package org.apache.skywalking.apm.plugin.jdbc.mysql.v6.define;

import org.apache.skywalking.apm.plugin.jdbc.define.AbstractDriverInstrumentation;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

/**
 * {@link DriverInstrumentation} presents that skywalking intercepts {@link com.mysql.jdbc.Driver}.
 */
public class DriverInstrumentation extends AbstractDriverInstrumentation {
    @Override
    protected ClassMatch enhanceClass() {
        return byMultiClassMatch("com.mysql.jdbc.Driver", "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.NonRegisteringDriver");
    }

    @Override
    protected String[] witnessClasses() {
        return new String[] {Constants.WITNESS_MYSQL_6X_CLASS};
    }
}
