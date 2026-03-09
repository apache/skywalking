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

package org.apache.skywalking.oap.server.core.source;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;

@Setter
@Getter
public abstract class AbstractLog extends Source {
    private String uniqueId;
    private long timestamp;
    private String serviceId;
    private String serviceInstanceId;
    private String endpointId;
    private String traceId;
    private String traceSegmentId;
    private int spanId;
    private ContentType contentType = ContentType.NONE;
    private String content;
    private byte[] tagsRawData;
    private List<Tag> tags = new ArrayList<>();
    private boolean error = false;

    @Override
    public String getEntityId() {
        return uniqueId;
    }

    /**
     * Called by the sink listener after common {@link AbstractLog} fields have been populated
     * from the {@link LogData.Builder}. Subclasses override this to pull custom fields
     * from tags, body content, or other LogData fields.
     *
     * <p>Default implementation is a no-op — the standard {@code Log} class does not need
     * additional preparation.
     */
    public void prepare(final LogData.Builder logData, final NamingControl namingControl) {
        // No-op by default. Subclasses override to populate custom fields.
    }
}
