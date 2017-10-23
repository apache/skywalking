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

package org.skywalking.apm.collector.agentstream.worker.segment.standardization;

import org.skywalking.apm.collector.agentregister.servicename.ServiceNameService;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author peng-yongsheng
 */
public class SpanIdExchanger implements IdExchanger<SpanDecorator> {

    private static SpanIdExchanger EXCHANGER;
    private ServiceNameService serviceNameService;

    public static SpanIdExchanger getInstance() {
        if (EXCHANGER == null) {
            EXCHANGER = new SpanIdExchanger();
        }
        return EXCHANGER;
    }

    public SpanIdExchanger() {
        serviceNameService = new ServiceNameService();
    }

    @Override public boolean exchange(SpanDecorator standardBuilder, int applicationId) {
        if (standardBuilder.getPeerId() == 0 && StringUtils.isNotEmpty(standardBuilder.getPeer())) {
            int peerId = ApplicationCache.get(standardBuilder.getPeer());
            if (peerId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setPeerId(peerId);
                standardBuilder.setPeer(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getOperationNameId() == 0 && StringUtils.isNotEmpty(standardBuilder.getOperationName())) {
            int operationNameId = serviceNameService.getOrCreate(applicationId, standardBuilder.getOperationName());
            if (operationNameId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setOperationNameId(operationNameId);
                standardBuilder.setOperationName(Const.EMPTY_STRING);
            }
        }
        return true;
    }
}
