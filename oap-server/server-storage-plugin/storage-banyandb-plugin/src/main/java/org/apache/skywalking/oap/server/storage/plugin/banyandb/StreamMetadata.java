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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.metadata.IndexRule;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
import org.apache.skywalking.banyandb.v1.client.metadata.TagFamilySpec;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@Slf4j
public class StreamMetadata {
    public static final String TAG_FAMILY_SEARCHABLE = "searchable";
    public static final String TAG_FAMILY_DATA = "data";

    public static final String ID = "id";

    private final Model model;

    private final Map<String, TagMetadata> tagDefinition;

    /**
     * Group of the stream
     */
    private final String group;
    /**
     * Spec of the stream
     */
    private final Stream stream;
    /**
     * Index rules attached to the stream
     */
    private final List<IndexRule> indexRules;

    private final int dataFamilySize;
    private final int searchableFamilySize;

    private final boolean useIdAsEntity;

    @Getter
    @Data
    public static class TagMetadata {
        private final String tagFamilyName;
        private final TagFamilySpec.TagSpec tagSpec;
        private final int tagIndex;
    }
}
