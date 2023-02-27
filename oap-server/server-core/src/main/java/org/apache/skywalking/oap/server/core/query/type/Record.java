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

package org.apache.skywalking.oap.server.core.query.type;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Record {
    /**
     * Literal string name for visualization.
     */
    private String name;
    /**
     * ID of this record.
     */
    private String id;
    /**
     * Usually an integer value as this is a metric to measure this entity ID.
     */
    private long value;
    /**
     * Have value, Only if the record has related trace id.
     * UI should show this as an attached value.
     */
    private String refId;

    public SelectedRecord toSelectedRecord() {
        final SelectedRecord result = new SelectedRecord();
        result.setName(getName());
        result.setId(getId());
        result.setRefId(getRefId());
        result.setValue(getValue());
        return result;
    }
}
