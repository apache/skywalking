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
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.ContentType;

@Setter
@Getter
public abstract class AbstractLog extends Source {
    private long timestamp;
    private String serviceId;
    private String serviceInstanceId;
    private String endpointId;
    private String endpointName;
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
        throw new UnexpectedException("getEntityId is not supported in AbstractLog source");
    }
}
