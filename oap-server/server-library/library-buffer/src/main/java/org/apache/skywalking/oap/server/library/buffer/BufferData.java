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

package org.apache.skywalking.oap.server.library.buffer;

import com.google.protobuf.GeneratedMessageV3;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;

/**
 * @author peng-yongsheng
 */
@Getter
public class BufferData<MESSAGE_TYPE extends GeneratedMessageV3> {
    private MESSAGE_TYPE messageType;
    @Setter private SegmentObject v2Segment;

    public BufferData(MESSAGE_TYPE messageType) {
        this.messageType = messageType;
    }
}
