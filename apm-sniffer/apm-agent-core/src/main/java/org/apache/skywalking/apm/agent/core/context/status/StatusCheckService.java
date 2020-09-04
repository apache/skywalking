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

package org.apache.skywalking.apm.agent.core.context.status;

import java.util.Arrays;
import lombok.Getter;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.context.status.StatusChecker.HIERARCHY_MATCH;
import static org.apache.skywalking.apm.agent.core.context.status.StatusChecker.OFF;

/**
 * The <code>StatusCheckService</code> determines whether the span should be tagged in error status if an exception
 * captured in the scope.
 */
@DefaultImplementor
public class StatusCheckService implements BootService {

    @Getter
    private String[] ignoredExceptionNames;

    private StatusChecker statusChecker;

    @Override
    public void prepare() throws Throwable {
        ignoredExceptionNames = Arrays.stream(Config.StatusCheck.IGNORED_EXCEPTIONS.split(","))
                                      .filter(StringUtil::isNotEmpty)
                                      .toArray(String[]::new);
        statusChecker = Config.StatusCheck.MAX_RECURSIVE_DEPTH > 0 ? HIERARCHY_MATCH : OFF;
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }

    public boolean isError(Throwable e) {
        return statusChecker.checkStatus(e);
    }
}
