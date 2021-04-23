/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.browser.manual.errorlog;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@SuperDataset
@Stream(name = BrowserErrorLogRecord.INDEX_NAME, scopeId = DefaultScopeDefine.BROWSER_ERROR_LOG, builder = BrowserErrorLogRecord.Builder.class, processor = RecordStreamProcessor.class)
public class BrowserErrorLogRecord extends Record {
    public static final String INDEX_NAME = "browser_error_log";
    public static final String UNIQUE_ID = "unique_id";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_VERSION_ID = "service_version_id";
    public static final String PAGE_PATH_ID = "pate_path_id";
    public static final String PAGE_PATH = "page_path";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERROR_CATEGORY = "error_category";
    public static final String DATA_BINARY = "data_binary";

    @Override
    public String id() {
        return uniqueId;
    }

    @Setter
    @Getter
    @Column(columnName = UNIQUE_ID)
    private String uniqueId;

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(columnName = SERVICE_VERSION_ID)
    private String serviceVersionId;

    @Setter
    @Getter
    @Column(columnName = PAGE_PATH_ID)
    private String pagePathId;

    @Setter
    @Getter
    @Column(columnName = PAGE_PATH, matchQuery = true)
    private String pagePath;

    @Setter
    @Getter
    @Column(columnName = TIMESTAMP)
    private long timestamp;

    @Setter
    @Getter
    @Column(columnName = ERROR_CATEGORY)
    private int errorCategory;

    @Setter
    @Getter
    @Column(columnName = DATA_BINARY)
    private byte[] dataBinary;

    public static class Builder implements StorageHashMapBuilder<BrowserErrorLogRecord> {
        @Override
        public BrowserErrorLogRecord storage2Entity(final Map<String, Object> dbMap) {
            BrowserErrorLogRecord record = new BrowserErrorLogRecord();
            record.setUniqueId((String) dbMap.get(UNIQUE_ID));
            record.setServiceId((String) dbMap.get(SERVICE_ID));
            record.setServiceVersionId((String) dbMap.get(SERVICE_VERSION_ID));
            record.setPagePathId((String) dbMap.get(PAGE_PATH_ID));
            record.setPagePath((String) dbMap.get(PAGE_PATH));
            record.setTimestamp(((Number) dbMap.get(TIMESTAMP)).longValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            record.setErrorCategory(((Number) dbMap.get(ERROR_CATEGORY)).intValue());
            String dataBinary = (String) dbMap.get(DATA_BINARY);
            if (StringUtil.isEmpty(dataBinary)) {
                record.setDataBinary(new byte[] {});
            } else {
                record.setDataBinary(Base64.getDecoder().decode(dataBinary));
            }
            return record;
        }

        @Override
        public Map<String, Object> entity2Storage(final BrowserErrorLogRecord storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(UNIQUE_ID, storageData.getUniqueId());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(SERVICE_VERSION_ID, storageData.getServiceVersionId());
            map.put(PAGE_PATH_ID, storageData.getPagePathId());
            map.put(PAGE_PATH, storageData.getPagePath());
            map.put(TIMESTAMP, storageData.getTimestamp());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ERROR_CATEGORY, storageData.getErrorCategory());
            if (CollectionUtils.isEmpty(storageData.getDataBinary())) {
                map.put(DATA_BINARY, Const.EMPTY_STRING);
            } else {
                map.put(DATA_BINARY, new String(Base64.getEncoder().encode(storageData.getDataBinary())));
            }
            return map;
        }
    }
}
