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

package org.apache.skywalking.oap.server.core.register;

import lombok.*;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author peng-yongsheng
 */
public abstract class RegisterSource extends StreamData implements StorageData {

    public static final String SEQUENCE = "sequence";
    public static final String REGISTER_TIME = "register_time";
    public static final String HEARTBEAT_TIME = "heartbeat_time";

    @Getter @Setter @Column(columnName = SEQUENCE) private int sequence;
    @Getter @Setter @Column(columnName = REGISTER_TIME) private long registerTime;
    @Getter @Setter @Column(columnName = HEARTBEAT_TIME) private long heartbeatTime;

    public boolean combine(RegisterSource registerSource) {
        if (heartbeatTime < registerSource.getHeartbeatTime()) {
            heartbeatTime = registerSource.getHeartbeatTime();
            return true;
        } else {
            return false;
        }
    }
}
