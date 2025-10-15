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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.TraceWrite;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceInsertRequest;

@Slf4j
public class BanyanDBRecordDAO extends AbstractBanyanDBDAO implements IRecordDAO {
    private final StorageBuilder<Record> storageBuilder;

    public BanyanDBRecordDAO(BanyanDBStorageClient client, StorageBuilder<Record> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        if (BanyanDB.TraceGroup.NONE != model.getBanyanDBModelExtension().getTraceGroup()) {
            TraceWrite traceWrite;
            if (record instanceof BanyanDBTrace) {
                if (record instanceof BanyanDBTrace.MergeTable) {
                    BanyanDBTrace.MergeTable mergeTable = (BanyanDBTrace.MergeTable) record;
                    MetadataRegistry.Schema mergeTableSchema = MetadataRegistry.INSTANCE.findRecordMetadata(mergeTable.getMergeTableName());

                    traceWrite = getClient().createTraceWrite(
                        schema.getMetadata().getGroup(),
                        mergeTable.getMergeTableName()
                    );
                    try {
                        for (String tag : mergeTableSchema.getTags()) {
                            if (tag.equals(mergeTable.getMergeTraceIdColumnName())) {
                                traceWrite.tag(
                                    tag,
                                    TagAndValue.stringTagValue(mergeTable.getTraceIdColumnValue())
                                );
                            } else if (tag.equals(mergeTable.getMergeTimestampColumnName())) {
                                traceWrite.tag(
                                    tag,
                                    TagAndValue.timestampTagValue(mergeTable.getTimestampColumnValue())
                                );
                            } else if (tag.equals(mergeTable.getMergeSpanIdColumnName())) {
                                traceWrite.tag(
                                    tag,
                                    TagAndValue.stringTagValue(mergeTable.getSpanIdColumnValue())
                                );
                            } else  {
                                traceWrite.tag(tag, TagAndValue.nullTagValue());
                            }
                        }
                    } catch (BanyanDBException e) {
                        log.error("fail to add tag", e);
                    }
                } else {
                    traceWrite = getClient().createTraceWrite(
                        schema.getMetadata().getGroup(),
                        model.getName()
                    );
                    Convert2Storage<TraceWrite> convert2Storage = new BanyanDBConverter.TraceToStorage(
                        schema, traceWrite);
                    storageBuilder.entity2Storage(record, convert2Storage);
                    traceWrite = convert2Storage.obtain();
                }

                traceWrite.span(((BanyanDBTrace) record).getSpanWrapper().toByteArray());
            } else {
                throw new IOException(
                    model.getName() + " is a banyandb trace model, the record should implement " + BanyanDBTrace.class.getName());
            }

            return new BanyanDBTraceInsertRequest(traceWrite);
        } else {
            StreamWrite streamWrite = getClient().createStreamWrite(
                schema.getMetadata().getGroup(), // group name
                model.getName(), // index-name
                record.id().build() // identity
            ); // set timestamp inside `BanyanDBConverter.StreamToStorage`
            Convert2Storage<StreamWrite> convert2Storage = new BanyanDBConverter.StreamToStorage(schema, streamWrite);
            storageBuilder.entity2Storage(record, convert2Storage);
            return new BanyanDBStreamInsertRequest(convert2Storage.obtain());
        }
    }
}
