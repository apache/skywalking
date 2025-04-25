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

package org.apache.skywalking.oap.server.tool.profile.exporter.test;

import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;

import java.io.IOException;
import java.util.List;

public class SpanAttachedEventQueryDAO implements ISpanAttachedEventQueryDAO {
    @Override
    public List<SpanAttachedEventRecord> querySpanAttachedEvents(SpanAttachedEventTraceType type, List<String> traceIds, @Nullable Duration duration) throws IOException {
        return null;
    }
}
