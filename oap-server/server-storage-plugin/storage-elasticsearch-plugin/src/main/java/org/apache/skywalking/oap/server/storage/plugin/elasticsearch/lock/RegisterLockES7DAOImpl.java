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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.document.DocumentField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.skywalking.oap.server.library.client.elasticsearch.client.impl.v7.ElasticSearch7Client.PRIMARY_TERM;
import static org.apache.skywalking.oap.server.library.client.elasticsearch.client.impl.v7.ElasticSearch7Client.SEQ_NO;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class RegisterLockES7DAOImpl extends EsDAO implements IRegisterLockDAO {

    private static final Logger logger = LoggerFactory.getLogger(RegisterLockES7DAOImpl.class);

    public RegisterLockES7DAOImpl(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public int getId(int scopeId, RegisterSource registerSource) {
        String id = scopeId + "";

        int sequence = Const.NONE;
        try {
            GetResponse response = getClient().get(RegisterLockIndex.NAME, id);
            if (response.isExists()) {
                Map<String, Object> source = response.getSource();
                sequence = ((Number) source.get(RegisterLockIndex.COLUMN_SEQUENCE)).intValue();

                sequence++;

                DocumentField ifSeqNo = response.getField(SEQ_NO);
                DocumentField ifPrimaryTerm = response.getField(PRIMARY_TERM);
                lock(id, sequence, ifSeqNo.getValue(), ifPrimaryTerm.getValue());
            }
        } catch (Throwable t) {
            logger.warn("Try to lock the row with the id {} failure, error message: {}", id, t.getMessage(), t);
            return Const.NONE;
        }
        return sequence;
    }

    private void lock(String id, int sequence, int seqNumber, int primaryTerm) throws IOException {
        JsonObject source = new JsonObject();
        source.addProperty(RegisterLockIndex.COLUMN_SEQUENCE, sequence);

        getClient().forceUpdate(RegisterLockIndex.NAME, id, source, seqNumber, primaryTerm);
    }
}


