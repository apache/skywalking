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
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * Alarm notify worker, do a simple route to alarm core after the aggregation persistence.
 */
public class AlarmNotifyWorker extends AbstractWorker<Metrics> {
    private AlarmEntrance entrance;

    public AlarmNotifyWorker(ModuleDefineHolder moduleDefineHolder) {
        super(moduleDefineHolder);
        this.entrance = new AlarmEntrance(moduleDefineHolder);
    }

    @Override
    public void in(Metrics metrics) {
        if (metrics instanceof WithMetadata) {
            entrance.forward(metrics);
        }
    }
}
