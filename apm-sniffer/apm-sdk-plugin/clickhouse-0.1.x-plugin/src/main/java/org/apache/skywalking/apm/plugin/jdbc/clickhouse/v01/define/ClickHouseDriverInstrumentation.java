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

package org.apache.skywalking.apm.plugin.jdbc.clickhouse.v01.define;

import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.jdbc.define.AbstractDriverInstrumentation;

import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link ClickHouseDriverInstrumentation} intercepts {@link ru.yandex.clickhouse.ClickHouseDriver}.
 *
 * @author IluckySi
 */
public class ClickHouseDriverInstrumentation extends AbstractDriverInstrumentation {

    public static final String ENHANCE_CLASS = "ru.yandex.clickhouse.ClickHouseDriver";

    protected NameMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
