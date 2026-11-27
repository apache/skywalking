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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;

/**
 * BanyanDBExtension represents extra metadata for models, but specific for BanyanDB usages.
 *
 * @since 9.3.0
 */
public class BanyanDBModelExtension {
    /**
     * timestampColumn is to identify which column in {@link Record} is providing the timestamp(millisecond) for BanyanDB.
     * BanyanDB stream requires a timestamp in milliseconds
     *
     * @since 9.3.0
     */
    @Getter
    @Setter
    private String timestampColumn;

    /**
     * traceIdColumn is to identify which column in the Trace model is used as the trace ID.
     *
     * @since 10.3.0
     */
    @Getter
    @Setter
    private String traceIdColumn;


    /**
     * spanIdColumn is to identify which column in the Trace model is used as the span ID.
     *
     * @since 10.3.0
     */
    @Getter
    @Setter
    private String spanIdColumn;

    /**
     * traceIndexRules is to identify which columns in the Trace model are used as the indexRule.
     * BanyanDB Trace model requires at least one traceIndexRules.
     *
     * @since 9.3.0
     */
    @Getter
    @Setter
    private List<TraceIndexRule> traceIndexRules;

    /**
     * indexMode indicates whether a metric is in the index mode.
     * Since 10.3.0, the installer will automatically create a virtual String tag 'id' for the SeriesID.
     * @since 10.2.0
     */
    @Getter
    @Setter
    private boolean indexMode;

    @Setter
    @Getter
    private BanyanDB.StreamGroup streamGroup = BanyanDB.StreamGroup.NONE;

    @Setter
    @Getter
    private BanyanDB.TraceGroup traceGroup = BanyanDB.TraceGroup.NONE;

    /**
     * Not null when the rows of this model are stored as an attachment of another model's trace. See
     * {@link BanyanDB.Trace.StoreAsAttachmentOf}.
     */
    @Setter
    @Getter
    private AttachmentTarget attachmentTarget;

    /**
     * The trace an attached model's rows are written into, with the target's own trace ID, timestamp and span ID
     * columns. The attached model's {@link #getTraceIdColumn()}, {@link #getTimestampColumn()} and
     * {@link #getSpanIdColumn()} name the columns whose values go into them.
     */
    @Getter
    @RequiredArgsConstructor
    public static class AttachmentTarget {
        private final String targetName;
        private final String targetTraceIdColumn;
        private final String targetTimestampColumn;
        private final String targetSpanIdColumn;
    }

    @RequiredArgsConstructor
    public static class TraceIndexRule {
        @Getter
        private final String name;
        @Getter
        private final String[] columns;
        @Getter
        private final String orderByColumn;
    }
}
