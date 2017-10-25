/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.worker.segment.origin.define;

import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author peng-yongsheng
 */
public class SegmentEsTableDefine extends ElasticSearchTableDefine {

    public SegmentEsTableDefine() {
        super(SegmentTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 10;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(SegmentTable.COLUMN_DATA_BINARY, ElasticSearchColumnDefine.Type.Binary.name()));
    }
}
