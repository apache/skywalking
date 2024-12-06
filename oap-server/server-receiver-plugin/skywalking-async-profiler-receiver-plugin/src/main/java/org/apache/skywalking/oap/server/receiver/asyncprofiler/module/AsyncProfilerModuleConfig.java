/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.receiver.asyncprofiler.module;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class AsyncProfilerModuleConfig extends ModuleConfig {
    /**
     * Used to manage the maximum size of the jfr file that can be received, the unit is Byte
     * default is 30M
     */
    private int jfrMaxSize = 30 * 1024 * 1024;
    /**
     * default is true
     * <p>
     * If memoryParserEnabled is true, then AsyncProfilerByteBufCollectionObserver will be enabled
     * will use memory to receive jfr files without writing files (this is currently used).
     * This can prevent the oap server from crashing due to no volume mounting.
     * <p>
     * If memoryParserEnabled is false, then AsyncProfilerFileCollectionObserver will be enabled
     * which uses createTemp to write files and then reads the files for parsing.
     * The advantage of this is that it reduces memory and prevents the oap server from crashing due to
     * insufficient memory, but it may report an error due to no volume mounting.
     */
    private boolean memoryParserEnabled = true;
}
