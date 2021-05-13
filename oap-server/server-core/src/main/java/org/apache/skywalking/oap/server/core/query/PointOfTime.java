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

package org.apache.skywalking.oap.server.core.query;

import lombok.Getter;
import org.apache.skywalking.oap.server.core.Const;

/**
 * PointOfTime represents any point of time based on different precisions.
 */
@Getter
public class PointOfTime {
    private long point;

    public PointOfTime(long point) {
        this.point = point;
    }

    /**
     * @return the row id
     */
    public String id(String entityId) {
        // null means scope = all or unexpected entity.
        if (entityId == null) {
            return String.valueOf(point);
        } else {
            return point + Const.ID_CONNECTOR + entityId;
        }
    }
}