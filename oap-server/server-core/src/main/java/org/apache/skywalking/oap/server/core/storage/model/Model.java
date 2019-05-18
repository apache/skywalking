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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.List;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.storage.Downsampling;
import org.apache.skywalking.oap.server.core.storage.ttl.*;

/**
 * @author peng-yongsheng
 */
@Getter
public class Model {
    private final String name;
    private final boolean deleteHistory;
    private final List<ModelColumn> columns;
    private final int scopeId;
    private final TTLCalculator ttlCalculator;

    public Model(String name, List<ModelColumn> columns, boolean deleteHistory,
        int scopeId, Downsampling downsampling) {
        this.columns = columns;
        this.deleteHistory = deleteHistory;
        this.scopeId = scopeId;

        switch (downsampling) {
            case Minute:
                this.name = name;
                this.ttlCalculator = new MinuteTTLCalculator();
                break;
            case Hour:
                this.name = name + Const.ID_SPLIT + Downsampling.Hour.getName();
                this.ttlCalculator = new HourTTLCalculator();
                break;
            case Day:
                this.name = name + Const.ID_SPLIT + Downsampling.Day.getName();
                this.ttlCalculator = new DayTTLCalculator();
                break;
            case Month:
                this.name = name + Const.ID_SPLIT + Downsampling.Month.getName();
                this.ttlCalculator = new MonthTTLCalculator();
                break;
            case Second:
                this.name = name;
                this.ttlCalculator = new SecondTTLCalculator();
                break;
            default:
                throw new UnexpectedException("Unexpected downsampling setting.");
        }
    }
}
