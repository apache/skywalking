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

import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.exporter.ExporterModule;
import org.apache.skywalking.oap.server.core.exporter.LogExportService;
import org.apache.skywalking.oap.server.core.exporter.TraceExportService;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

public class ExportRecordWorker extends AbstractWorker<Record> {
    private TraceExportService traceExportService;
    private LogExportService logExportService;

    public ExportRecordWorker(ModuleDefineHolder moduleDefineHolder) {
        super(moduleDefineHolder);
    }

    @Override
    public void in(Record record) {
        if (record instanceof SegmentRecord) {
            if (traceExportService != null || getModuleDefineHolder().has(ExporterModule.NAME)) {
                if (traceExportService == null) {
                    traceExportService = getModuleDefineHolder().find(ExporterModule.NAME)
                                                                .provider()
                                                                .getService(TraceExportService.class);
                }
                if (traceExportService.isEnabled()) {
                    traceExportService.export((SegmentRecord) record);
                }
            }
        } else if (record instanceof LogRecord) {
            if (logExportService != null || getModuleDefineHolder().has(ExporterModule.NAME)) {
                if (logExportService == null) {
                    logExportService = getModuleDefineHolder().find(ExporterModule.NAME)
                                                              .provider()
                                                              .getService(LogExportService.class);
                }
                if (logExportService.isEnabled()) {
                    logExportService.export((LogRecord) record);
                }
            }
        }
    }
}
