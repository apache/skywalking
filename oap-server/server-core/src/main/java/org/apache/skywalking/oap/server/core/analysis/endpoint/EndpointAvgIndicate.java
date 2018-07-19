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

package org.apache.skywalking.oap.server.core.analysis.endpoint;

import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.AvgIndicate;

/**
 * @author peng-yongsheng
 */
public class EndpointAvgIndicate extends AvgIndicate {

    @Getter private final int id;

    public EndpointAvgIndicate(int id, long timeBucket) {
        super(timeBucket);
        this.id = id;
    }

    public void setLatency(long latency) {
        setValue(latency);
    }

    public long getLatency() {
        return getValue();
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + id;
        //TODO How?
//        result = 31 * result + getTimeBucket();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        EndpointAvgIndicate indicate = (EndpointAvgIndicate)obj;
        if (id != indicate.id)
            return false;
        if (getTimeBucket() != indicate.getTimeBucket())
            return false;

        return true;
    }
}
