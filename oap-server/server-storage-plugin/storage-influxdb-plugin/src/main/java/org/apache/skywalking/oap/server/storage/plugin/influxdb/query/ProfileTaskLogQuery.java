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
package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

public class ProfileTaskLogQuery implements IProfileTaskLogQueryDAO {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private InfluxClient client;

    public ProfileTaskLogQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList(String taskId) throws IOException {
        return null;
    }
}
