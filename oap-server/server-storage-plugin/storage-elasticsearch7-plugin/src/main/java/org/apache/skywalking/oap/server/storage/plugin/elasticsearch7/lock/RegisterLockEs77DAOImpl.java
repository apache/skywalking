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
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.lock;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockIndex;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.Es7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.client.ElasticSearch7Client;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * @author kezhenxu94
 */
public class RegisterLockEs77DAOImpl extends Es7DAO implements IRegisterLockDAO {

    private static final Logger logger = LoggerFactory.getLogger(RegisterLockEs77DAOImpl.class);

    public RegisterLockEs77DAOImpl(ElasticSearch7Client client) {
        super(client);
    }

    @Override
    public int getId(int scopeId, RegisterSource registerSource) {
        String id = String.valueOf(scopeId);

        int sequence = Const.NONE;
        try {
            GetResponse response = getClient().get(RegisterLockIndex.NAME, id);
            if (response.isExists()) {
                Map<String, Object> source = response.getSource();

                sequence = ((Number) source.get(RegisterLockIndex.COLUMN_SEQUENCE)).intValue();

                sequence++;

                lock(id, sequence, response.getSeqNo(), response.getPrimaryTerm());
            }
        } catch (Throwable t) {
            logger.warn("Try to lock the row with the id {} failure, error message: {}", id, t.getMessage(), t);
            return Const.NONE;
        }
        return sequence;
    }

    private void lock(String id, int sequence, final long seqNo, long primaryTerm) throws IOException {

        XContentBuilder source = XContentFactory.jsonBuilder().startObject();
        source.field(RegisterLockIndex.COLUMN_SEQUENCE, sequence);
        source.endObject();

        getClient().forceUpdate(RegisterLockIndex.NAME, id, source, seqNo, primaryTerm);
    }
}


