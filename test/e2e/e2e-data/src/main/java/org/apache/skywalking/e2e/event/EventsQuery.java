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

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.skywalking.e2e.AbstractQuery;

@Getter
@Setter
@Accessors(fluent = true)
public class EventsQuery extends AbstractQuery<EventsQuery> {
    private String uuid;
    private String pageNum = "1";
    private String pageSize = "20";
    private String needTotal = "true";
}
