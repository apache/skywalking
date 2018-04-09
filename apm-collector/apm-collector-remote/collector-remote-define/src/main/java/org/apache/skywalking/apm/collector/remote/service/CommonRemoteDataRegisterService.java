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

package org.apache.skywalking.apm.collector.remote.service;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class CommonRemoteDataRegisterService implements RemoteDataRegisterService, RemoteDataIDGetter, RemoteDataInstanceCreatorGetter {

    private static final Logger logger = LoggerFactory.getLogger(CommonRemoteDataRegisterService.class);

    private Integer id;
    private final Map<Class<? extends RemoteData>, Integer> dataClassMapping;
    private final Map<Integer, RemoteDataInstanceCreator> dataInstanceCreatorMapping;

    public CommonRemoteDataRegisterService() {
        this.id = 1;
        this.dataClassMapping = new HashMap<>();
        this.dataInstanceCreatorMapping = new HashMap<>();
    }

    @Override public void register(Class<? extends RemoteData> dataClass, RemoteDataInstanceCreator instanceCreator) {
        if (!dataClassMapping.containsKey(dataClass)) {
            dataClassMapping.put(dataClass, this.id);
            dataInstanceCreatorMapping.put(this.id, instanceCreator);
            this.id++;
        } else {
            logger.warn("The data class {} was registered.", dataClass.getName());
        }
    }

    @Override
    public Integer getRemoteDataId(Class<? extends RemoteData> dataClass) throws RemoteDataMappingIdNotFoundException {
        if (dataClassMapping.containsKey(dataClass)) {
            return dataClassMapping.get(dataClass);
        } else {
            throw new RemoteDataMappingIdNotFoundException("Could not found the id of remote data class " + dataClass.getName());
        }
    }

    @Override public RemoteDataInstanceCreator getInstanceCreator(
        Integer remoteDataId) throws RemoteDataInstanceCreatorNotFoundException {
        if (dataInstanceCreatorMapping.containsKey(remoteDataId)) {
            return dataInstanceCreatorMapping.get(remoteDataId);
        } else {
            throw new RemoteDataInstanceCreatorNotFoundException("Could not found the instance creator of remote data id " + remoteDataId);
        }
    }
}
