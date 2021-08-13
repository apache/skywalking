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

package org.apache.skywalking.banyandb.v1.client;

import com.google.protobuf.Timestamp;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.Banyandb;

@RequiredArgsConstructor
@Getter(AccessLevel.PROTECTED)
public class TimestampRange {
    private final long begin;
    private final long end;

    /**
     * @return TimeRange accordingly.
     */
    Banyandb.TimeRange build() {
        final Banyandb.TimeRange.Builder builder = Banyandb.TimeRange.newBuilder();
        builder.setBegin(Timestamp.newBuilder()
                                  .setSeconds(begin / 1000)
                                  .setNanos((int) (begin % 1000 * 1000)));
        builder.setBegin(Timestamp.newBuilder()
                                  .setSeconds(end / 1000)
                                  .setNanos((int) (end % 1000 * 1000)));
        return builder.build();
    }
}
