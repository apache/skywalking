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

package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import static java.util.Objects.isNull;
import static org.apache.skywalking.apm.util.StringUtil.isBlank;

public class EventQueryService implements Service {

    private final ModuleManager moduleManager;

    private IEventQueryDAO dao;

    public EventQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IEventQueryDAO getDao() {
        if (dao == null) {
            dao = moduleManager.find(StorageModule.NAME).provider().getService(IEventQueryDAO.class);
        }
        return dao;
    }

    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        if (isBlank(condition.getUuid()) && isDurationInvalid(condition.getTime())) {
            throw new IllegalArgumentException("time field is required when uuid is absent.");
        }
        return getDao().queryEvents(condition);
    }

    boolean isDurationInvalid(final Duration duration) {
        return isNull(duration) || (isBlank(duration.getStart()) || isBlank(duration.getEnd()));
    }
}
