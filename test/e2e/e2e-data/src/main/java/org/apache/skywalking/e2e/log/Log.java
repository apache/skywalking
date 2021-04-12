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

package org.apache.skywalking.e2e.log;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.skywalking.e2e.common.KeyValue;

@Data
@Accessors(chain = true)
public class Log {

    private String serviceName;
    private String serviceId;
    private String serviceInstanceName;
    private String serviceInstanceId;
    private String endpointName;
    private String endpointId;
    private String traceId;
    private Long timestamp;
    private String contentType;
    private String content;
    private List<KeyValue> tags;
}
