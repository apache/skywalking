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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public class H2RegisterLockInstaller {
    public static final String LOCK_TABLE_NAME = "register_lock";

    private static final Logger logger = LoggerFactory.getLogger(H2RegisterLockInstaller.class);

    /**
     * For H2 storage, no concurrency situation, so, on lock table required. If someone wants to implement a storage by
     * referring H2, please consider to create a LOCK table.
     *
     * @param client
     * @throws StorageException
     */
    public void install(Client client) throws StorageException {

    }
}
