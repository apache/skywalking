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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.io.Bufs;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class CodecUtilsTest {

    @Test
    public void encode() {
        SWContextCarrier swContextCarrier = makeSWContextCarrier();
        assertSwContextCarrier(swContextCarrier, CodecUtils.decode(CodecUtils.encode(swContextCarrier)));

        swContextCarrier = makeSWContextCarrier();
        assertSwContextCarrier(swContextCarrier, CodecUtils.decode(CodecUtils.encode(swContextCarrier)));

        swContextCarrier = makeSWContextCarrier();
        assertSwContextCarrier(swContextCarrier, CodecUtils.decode(CodecUtils.encode(swContextCarrier)));

        swContextCarrier = new SWContextCarrier();
        assertSwContextCarrier(swContextCarrier, CodecUtils.decode(Bufs.EMPTY));
    }

    private SWContextCarrier makeSWContextCarrier() {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                next.setHeadValue(UUID.randomUUID().toString());
            }
        }
        SWContextCarrier swContextCarrier = new SWContextCarrier();
        swContextCarrier.setContextCarrier(contextCarrier);
        swContextCarrier.setOperationName(UUID.randomUUID().toString());
        return swContextCarrier;
    }

    private void assertSwContextCarrier(SWContextCarrier expected, SWContextCarrier actual) {
        assertThat(expected.getOperationName(), is(actual.getOperationName()));
        Map<String, String> data = new HashMap<>();
        if (actual.getCarrier() == null) {
            assertNull(expected.getCarrier());
        } else {
            CarrierItem next = expected.getCarrier().items();
            while (next.hasNext()) {
                next = next.next();
                data.put(next.getHeadKey(), next.getHeadValue());
            }
            next = actual.getCarrier().items();
            while (next.hasNext()) {
                next = next.next();
                assertThat(next.getHeadValue(), is(data.get(next.getHeadKey())));
            }
        }

    }
}
