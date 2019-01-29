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

package org.apache.skywalking.oap.server.core.analysis.worker;

import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * TopN is a special process, which hold a certain size of windows,
 * and cache all top N records, save to the persistence in low frequence.
 *
 * @author wusheng
 */
public enum TopNProcess {
    INSTANCE;

    public void create(ModuleManager moduleManager, Class<? extends TopN> topN) {
        String modelName = StorageEntityAnnotationUtils.getModelName(topN);
        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(topN);
    }

    public void in(TopNDatabaseStatement statement) {

    }
}
