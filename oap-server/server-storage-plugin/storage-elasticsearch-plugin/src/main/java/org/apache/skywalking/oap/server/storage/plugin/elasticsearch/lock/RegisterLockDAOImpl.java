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
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterLockDAOImpl extends EsDAO implements IRegisterLockDAO {

    private static final Logger logger = LoggerFactory.getLogger(RegisterLockDAOImpl.class);

    private final int timeout;

    public RegisterLockDAOImpl(ElasticSearchClient client, int timeout) {
        super(client);
        this.timeout = timeout;
    }

    @Override public int tryLockAndIncrement(Scope scope) {
        String id = String.valueOf(scope.ordinal());

        int sequence = Const.NONE;
        try {
            GetResponse response = getClient().get(RegisterLockIndex.NAME, id);
            if (response.isExists()) {
                Map<String, Object> source = response.getSource();

                long expire = ((Number)source.get(RegisterLockIndex.COLUMN_EXPIRE)).longValue();
                boolean lockable = (boolean)source.get(RegisterLockIndex.COLUMN_LOCKABLE);
                sequence = ((Number)source.get(RegisterLockIndex.COLUMN_SEQUENCE)).intValue();
                long version = response.getVersion();

                sequence++;

                if (lockable || System.currentTimeMillis() > expire) {
                    lock(id, sequence, timeout, version);
                } else {
                    TimeUnit.SECONDS.sleep(1);
                    return Const.NONE;
                }
            }
        } catch (Throwable t) {
            logger.warn("Try to lock the row with the id {} failure, error message: {}", id, t.getMessage());
            return Const.NONE;
        }
        return sequence;
    }

    private void lock(String id, int sequence, int timeout, long version) throws IOException {
        XContentBuilder source = XContentFactory.jsonBuilder().startObject();
        source.field(RegisterLockIndex.COLUMN_EXPIRE, System.currentTimeMillis() + timeout);
        source.field(RegisterLockIndex.COLUMN_LOCKABLE, false);
        source.field(RegisterLockIndex.COLUMN_SEQUENCE, sequence);
        source.endObject();

        getClient().forceUpdate(RegisterLockIndex.NAME, id, source, version);
    }

    @Override public void releaseLock(Scope scope) {
        String id = String.valueOf(scope.ordinal());

        try {
            XContentBuilder source = XContentFactory.jsonBuilder().startObject();
            source.field(RegisterLockIndex.COLUMN_LOCKABLE, true);
            source.endObject();

            getClient().forceUpdate(RegisterLockIndex.NAME, id, source);
        } catch (Throwable t) {
            logger.error("{} inventory release lock failure.", scope.name(), t);
        }
    }
}
