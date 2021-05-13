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

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;
import org.assertj.core.api.Assertions;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProfileTaskMatcher extends AbstractMatcher<ProfileTask> {

    private String id;
    private String serviceId;
    private String endpointName;
    private String startTime;
    private String duration;
    private String minDurationThreshold;
    private String dumpPeriod;
    private String maxSamplingCount;

    private List<ProfileTaskLogMatcher> logs;

    @Override
    public void verify(ProfileTask task) {
        doVerify(id, task.getId());
        doVerify(serviceId, task.getServiceId());
        doVerify(endpointName, task.getEndpointName());
        doVerify(startTime, task.getStartTime());
        doVerify(duration, task.getDuration());
        doVerify(minDurationThreshold, task.getMinDurationThreshold());
        doVerify(dumpPeriod, task.getDumpPeriod());

        // verify logs
        Assertions.assertThat(task.getLogs()).hasSameSizeAs(this.logs);
        int size = this.getLogs().size();
        for (int i = 0; i < size; i++) {
            this.getLogs().get(i).verify(task.getLogs().get(i));
        }
    }

}
