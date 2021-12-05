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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Log;

import java.util.List;

public class LogDeserializer extends AbstractBanyanDBDeserializer<Log> {
    public LogDeserializer() {
        super(LogRecord.INDEX_NAME, ImmutableList.of(
                        AbstractLogRecord.SERVICE_ID, AbstractLogRecord.SERVICE_INSTANCE_ID,
                        AbstractLogRecord.ENDPOINT_ID, AbstractLogRecord.TRACE_ID, AbstractLogRecord.TRACE_SEGMENT_ID,
                        AbstractLogRecord.SPAN_ID),
                ImmutableList.of(AbstractLogRecord.CONTENT_TYPE, AbstractLogRecord.CONTENT, AbstractLogRecord.TAGS_RAW_DATA));
    }

    @Override
    public Log map(RowEntity row) {
        Log log = new Log();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        log.setServiceId((String) searchable.get(0).getValue());
        log.setServiceInstanceId((String) searchable.get(1).getValue());
        log.setEndpointId((String) searchable.get(2).getValue());
        log.setTraceId((String) searchable.get(3).getValue());
        log.setTimestamp(row.getTimestamp());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        if (data.get(2).getValue() == null || ((ByteString) data.get(2).getValue()).isEmpty()) {
            log.setContent("");
        } else {
            try {
                // Don't read the tags as they have been in the data binary already.
                LogTags logTags = LogTags.parseFrom((ByteString) data.get(2).getValue());
                for (final KeyStringValuePair pair : logTags.getDataList()) {
                    log.getTags().add(new KeyValue(pair.getKey(), pair.getValue()));
                }
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
        return log;
    }
}
