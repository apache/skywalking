/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.event;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.skywalking.e2e.common.KeyValue;

@Data
@Accessors(chain = true)
public class Event {
    private String uuid;

    private Source source;

    private String name;

    private String type;

    private String message;

    private List<KeyValue> parameters;

    private String startTime;

    private String endTime;

    @Data
    @Accessors(chain = true)
    static class Source {
        private String service;

        private String serviceInstance;

        private String endpoint;
    }
}
