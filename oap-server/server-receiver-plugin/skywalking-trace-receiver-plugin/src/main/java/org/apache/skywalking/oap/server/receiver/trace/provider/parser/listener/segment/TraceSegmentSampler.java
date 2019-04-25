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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.segment;

import java.util.List;
import org.apache.skywalking.apm.network.language.agent.UniqueId;

/**
 * The sampler makes the sampling mechanism works at backend side.
 *
 * The sample check mechanism is very easy and effective when backend run in cluster mode. Based on traceId, which is
 * constituted by 3 Long, and according to GlobalIdGenerator, the last four number of the last Long is a sequence, so it
 * is suitable for sampling.
 *
 * Set rate = x
 *
 * Then divide last Long in TraceId by 10000,  y = x % 10000
 *
 * Sample result: [0,y) sampled, (y,~) ignored
 *
 * @author wusheng
 */
public class TraceSegmentSampler {
    private int sampleRate = 10000;

    public TraceSegmentSampler(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean shouldSample(UniqueId uniqueId) {
        List<Long> idPartsList = uniqueId.getIdPartsList();
        if (idPartsList.size() == 3) {
            Long lastLong = idPartsList.get(2);
            long sampleValue = lastLong % 10000;
            if (sampleValue < sampleRate) {
                return true;
            }
        }
        return false;
    }
}
