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

package org.apache.skywalking.apm.agent.core.context;

import java.util.Iterator;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Agent.NAMESPACE;

public class CarrierItem implements Iterator<CarrierItem> {
    private String headKey;
    private String headValue;
    private CarrierItem next;

    public CarrierItem(String headKey, String headValue) {
        this(headKey, headValue, null);
    }

    public CarrierItem(String headKey, String headValue, CarrierItem next) {
        if (StringUtil.isEmpty(NAMESPACE)) {
            this.headKey = headKey;
        } else {
            this.headKey = NAMESPACE + "-" + headKey;
        }
        this.headValue = headValue;
        this.next = next;
    }

    public String getHeadKey() {
        return headKey;
    }

    public String getHeadValue() {
        return headValue;
    }

    public void setHeadValue(String headValue) {
        this.headValue = headValue;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public CarrierItem next() {
        return next;
    }

    @Override
    public void remove() {

    }
}
