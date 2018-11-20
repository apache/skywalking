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

import java.io.IOException;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.module.Service;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class AlarmQueryService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(AlarmQueryService.class);

    private final ModuleManager moduleManager;
    private IAlarmQueryDAO alarmQueryDAO;

    public AlarmQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IAlarmQueryDAO getAlarmQueryDAO() {
        if (alarmQueryDAO == null) {
            alarmQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IAlarmQueryDAO.class);
        }
        return alarmQueryDAO;
    }

    public Alarms getAlarm(final Scope scope, final String keyword, final Pagination paging, final long startTB,
        final long endTB) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);
        return getAlarmQueryDAO().getAlarm(scope, keyword, page.getLimit(), page.getFrom(), startTB, endTB);
    }
}
