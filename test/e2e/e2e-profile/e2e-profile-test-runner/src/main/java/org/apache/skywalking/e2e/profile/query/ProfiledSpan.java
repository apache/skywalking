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

package org.apache.skywalking.e2e.profile.query;

import com.google.common.primitives.Ints;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ProfiledSpan implements Comparable<ProfiledSpan> {

    private String spanId;
    private String parentSpanId;
    private String serviceCode;
    private String startTime;
    private String endTime;
    private String endpointName;

    @Override
    public int compareTo(ProfiledSpan o) {
        return Ints.compare(Integer.parseInt(spanId), Integer.parseInt(o.spanId));
    }
}
