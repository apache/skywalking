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

package org.apache.skywalking.oap.server.core.analysis.worker;

import org.apache.skywalking.oap.server.core.alarm.AlarmEntrance;
import org.apache.skywalking.oap.server.core.alarm.AlarmSupported;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Alarm notify worker, do a simple route to alarm core after the aggregation persistence.
 *
 * @author wusheng
 */
public class AlarmNotifyWorker extends AbstractWorker<Indicator> {
    private ModuleManager moduleManager;
    private AlarmEntrance entrance;

    public AlarmNotifyWorker(int workerId, ModuleManager moduleManager) {
        super(workerId);
        this.moduleManager = moduleManager;
        this.entrance = new AlarmEntrance(moduleManager);
    }

    @Override public void in(Indicator indicator) {
        if (indicator instanceof AlarmSupported) {
            entrance.forward(indicator);
        }
    }
}
