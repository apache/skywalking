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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import org.apache.skywalking.oap.query.graphql.type.NowTime;
import org.apache.skywalking.oap.query.graphql.type.Timezone;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author kdump
 */
public class TimeQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;

    public TimeQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public NowTime getNowTime() {
        NowTime nowTime = new NowTime();
        nowTime.setTime(new Date().getTime());
        return nowTime;
    }

    public Timezone getTimezone() {
        SimpleDateFormat timezoneFormat = new SimpleDateFormat("ZZZZZ");
        Timezone timezone = new Timezone();
        timezone.setTimezone(timezoneFormat.format(new Date()));
        return timezone;
    }
}
