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

package org.apache.skywalking.apm.plugin.spring.concurrent.match;

import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

/**
 * {@link FailedCallbackMatch} match the class that inherited <code>org.springframework.util.concurrent.FailureCallback</code>
 * and not inherited <code>org.springframework.util.concurrent.SuccessCallback</code>
 */
public class FailedCallbackMatch extends EitherInterfaceMatch {

    private static final String MATCH_INTERFACE = "org.springframework.util.concurrent.FailureCallback";
    private static final String MUTEX_INTERFACE = "org.springframework.util.concurrent.SuccessCallback";

    private FailedCallbackMatch() {

    }

    @Override
    public String getMatchInterface() {
        return MATCH_INTERFACE;
    }

    @Override
    public String getMutexInterface() {
        return MUTEX_INTERFACE;
    }

    public static ClassMatch failedCallbackMatch() {
        return new FailedCallbackMatch();
    }
}
