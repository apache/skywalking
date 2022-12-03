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

package org.apache.skywalking.oap.server.core.status;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * The server status service provides the indicators for the current server status.
 * Notice, this should not be treated as a kind of health checker or self telemetry.
 * For more, this helps modules to be aware of current OAP server status.
 *
 * @since 9.4.0
 */
@RequiredArgsConstructor
public class ServerStatusService implements Service {
    private final ModuleManager manager;
    private BootingStatus bootingStatus = new BootingStatus();

    public void bootedNow(long uptime) {
        bootingStatus.setBooted(true);
        bootingStatus.setUptime(uptime);
        manager.find(TelemetryModule.NAME)
               .provider()
               .getService(MetricsCreator.class)
               .createGauge("uptime", "oap server start up time", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE)
               // Set uptime to second
               .setValue(uptime / 1000d);
    }

}
