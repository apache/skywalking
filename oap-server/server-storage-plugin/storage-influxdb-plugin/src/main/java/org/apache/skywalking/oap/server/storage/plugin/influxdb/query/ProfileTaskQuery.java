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

import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;

// TODO have not implement!
public class ProfileTaskQuery implements IProfileTaskQueryDAO {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final InfluxClient client;

    public ProfileTaskQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName, Long startTimeBucket,
                                         Long endTimeBucket, Integer limit) throws IOException {
        return Collections.emptyList();
    }
}
