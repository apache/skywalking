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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.DAO;

public interface IAlarmQueryDAO extends DAO {

    Gson GSON = new Gson();

    Alarms getAlarm(final Integer scopeId, final String keyword, final int limit, final int from, final long startTB,
                    final long endTB, final List<Tag> tags) throws IOException;

    /**
     * Parser the raw tags.
     */
    default void parserDataBinaryBase64(String dataBinaryBase64, List<KeyValue> tags) {
        parserDataBinary(Base64.getDecoder().decode(dataBinaryBase64), tags);
    }

    /**
     * Parser the raw tags.
     */
    default void parserDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        List<Tag> tagList = GSON.fromJson(new String(dataBinary, Charsets.UTF_8), new TypeToken<List<Tag>>() {
        }.getType());
        tagList.forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
    }
}
