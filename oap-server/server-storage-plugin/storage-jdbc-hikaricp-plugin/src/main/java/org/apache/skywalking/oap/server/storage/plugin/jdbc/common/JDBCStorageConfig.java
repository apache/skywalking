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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import java.util.Properties;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class JDBCStorageConfig extends ModuleConfig {
    protected int metadataQueryMaxSize = 5000;
    /**
     * The maximum size of batch size of SQL execution
     */
    protected int maxSizeOfBatchSql = 2000;
    /**
     * async batch execute pool size
     */
    protected int asyncBatchPersistentPoolSize  = 4;
    protected Properties properties;
}
