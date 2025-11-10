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

package org.apache.skywalking.oap.query.debug;

import com.google.gson.Gson;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
@ExceptionHandler(StatusQueryExceptionHandler.class)
public class AlarmStatusQueryHandler {
    private final static Gson GSON = new Gson();
    private final ModuleManager moduleManager;
    private AlarmStatusQueryService queryService;

    public AlarmStatusQueryHandler(final ModuleManager manager) {
        this.moduleManager = manager;
    }

    private AlarmStatusQueryService getQueryService() {
        if (queryService == null) {
            queryService = moduleManager.find(StatusQueryModule.NAME)
                                                                 .provider().getService(AlarmStatusQueryService.class);
        }
        return queryService;
    }

    @Get("/status/alarm/rules")
    public HttpResponse getAlarmRules() {
        String result = GSON.toJson(getQueryService().getAlarmRules());
        return HttpResponse.of(MediaType.JSON_UTF_8, result);
    }

    @Get("/status/alarm/{ruleId}")
    public HttpResponse getAlarmRuleByName(@Param("ruleId") String ruleName) {
        String result = GSON.toJson(getQueryService().getAlarmRuleById(ruleName));
        return HttpResponse.of(MediaType.JSON_UTF_8, result);
    }

    @Get("/status/alarm/{ruleId}/{entityName}")
    public HttpResponse getAlarmRuleContext(@Param("ruleId") String ruleId, @Param("entityName") String entityName) {
        String result = GSON.toJson(getQueryService().getAlarmRuleContext(ruleId, entityName));
        return HttpResponse.of(MediaType.JSON_UTF_8, result);
    }
}
