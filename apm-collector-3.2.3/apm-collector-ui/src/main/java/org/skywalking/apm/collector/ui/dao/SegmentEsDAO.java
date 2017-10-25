/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Base64;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentEsDAO extends EsDAO implements ISegmentDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsDAO.class);

    @Override public TraceSegmentObject load(String segmentId) {
        GetResponse response = getClient().prepareGet(SegmentTable.TABLE, segmentId).get();
        Map<String, Object> source = response.getSource();
        String dataBinaryBase64 = (String)source.get(SegmentTable.COLUMN_DATA_BINARY);
        if (StringUtils.isNotEmpty(dataBinaryBase64)) {
            byte[] dataBinary = Base64.getDecoder().decode(dataBinaryBase64);
            try {
                return TraceSegmentObject.parseFrom(dataBinary);
            } catch (InvalidProtocolBufferException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
