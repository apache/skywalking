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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch;

import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * IndicesMetadataCache hosts all pseudo real time metadata of indices.
 */
@Slf4j
public class IndicesMetadataCache {
    public static IndicesMetadataCache INSTANCE = new IndicesMetadataCache();

    private volatile HashSet<String> existingIndices;

    private IndicesMetadataCache() {
        existingIndices = new HashSet<>();
    }

    public void update(List<String> indices) {
        existingIndices = new HashSet<>(indices);
    }

    /**
     * @return true if given index name exists currently.
     */
    public boolean isExisting(String index) {
        return existingIndices.contains(index);
    }
}
