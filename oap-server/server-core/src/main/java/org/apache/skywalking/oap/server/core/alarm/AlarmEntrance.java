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

package org.apache.skywalking.oap.server.core.alarm;

import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author wusheng
 */
public class AlarmEntrance {
    private ModuleManager moduleManager;
    private ServiceInventoryCache serviceInventoryCache;
    private IndicatorNotify indicatorNotify;
    private ReentrantLock initLock;

    public AlarmEntrance(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.initLock = new ReentrantLock();
    }

    public void forward(Indicator indicator) {
        if (!moduleManager.has(AlarmModule.NAME)) {
            return;
        }

        init();

        AlarmMeta alarmMeta = ((AlarmSupported)indicator).getAlarmMeta();

        MetaInAlarm metaInAlarm = null;
        switch (alarmMeta.getScope()) {
            case Service:
                int serviceId = Integer.parseInt(alarmMeta.getId());
                ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);
                ServiceMetaInAlarm serviceMetaInAlarm = new ServiceMetaInAlarm();
                serviceMetaInAlarm.setIndicatorName(alarmMeta.getIndicatorName());
                serviceMetaInAlarm.setId(serviceId);
                serviceMetaInAlarm.setName(serviceInventory.getName());
                metaInAlarm = serviceMetaInAlarm;
                break;
            default:
                return;
        }

        indicatorNotify.notify(metaInAlarm, indicator);
    }

    private void init() {
        if (serviceInventoryCache == null) {
            initLock.lock();
            try {
                if (serviceInventoryCache == null) {
                    serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
                    indicatorNotify = moduleManager.find(AlarmModule.NAME).getService(IndicatorNotify.class);
                    indicatorNotify.init(new AlarmStandardPersistence());
                }
            } finally {
                initLock.unlock();
            }
        }
    }
}
