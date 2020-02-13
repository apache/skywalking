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

import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.exporter.ExporterModule;
import org.apache.skywalking.oap.server.core.exporter.MetricValuesExportService;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * A bridge worker. If the {@link ExporterModule} provider declared and provides a implementation of {@link
 * MetricValuesExportService}, forward the export data to it.
 */
public class ExportWorker extends AbstractWorker<ExportEvent> {
    private MetricValuesExportService exportService;

    public ExportWorker(ModuleDefineHolder moduleDefineHolder) {
        super(moduleDefineHolder);
    }

    @Override
    public void in(ExportEvent event) {
        if (exportService != null || getModuleDefineHolder().has(ExporterModule.NAME)) {
            if (exportService == null) {
                exportService = getModuleDefineHolder().find(ExporterModule.NAME)
                                                       .provider()
                                                       .getService(MetricValuesExportService.class);
            }
            exportService.export(event);
        }
    }

}
