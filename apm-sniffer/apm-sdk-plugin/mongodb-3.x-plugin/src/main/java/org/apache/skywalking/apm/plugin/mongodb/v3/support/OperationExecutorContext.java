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


package org.apache.skywalking.apm.plugin.mongodb.v3.support;

import com.mongodb.client.internal.OperationExecutor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author scolia
 */
public class OperationExecutorContext {

    // key: OperationExecutor; value: remotePeer
    private final Map<OperationExecutor, String> remotePeerMapping = new WeakHashMap<OperationExecutor, String>();

    private OperationExecutorContext() {
    }

    public static OperationExecutorContext getInstance() {
        return Holder.INSTANCE;
    }

    public void putRemotePeerMapping(OperationExecutor executor, String remotePeer) {
        remotePeerMapping.put(executor, remotePeer);
    }

    public String getRemotePeerMapping(OperationExecutor executor) {
        String remotePeer = remotePeerMapping.get(executor);
        return remotePeer != null ? remotePeer : MongoConstants.UNKNOWN_REMOTE_PEER;
    }

    private static class Holder {
        private static final OperationExecutorContext INSTANCE = new OperationExecutorContext();
    }
}
