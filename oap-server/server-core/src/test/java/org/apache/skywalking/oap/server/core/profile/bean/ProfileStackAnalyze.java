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
package org.apache.skywalking.oap.server.core.profile.bean;

import lombok.Data;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileStack;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Data
public class ProfileStackAnalyze {

    private ProfileStackData data;
    private List<ProfileStackElementMatcher> expected;

    public void analyzeAndAssert() {
        List<ProfileStack> stacks = data.transform();
        ProfileAnalyzation analyze = ProfileAnalyzer.analyze(stacks);

        assertEquals(analyze.getStack().size(), expected.size());
        for (int i = 0; i < analyze.getStack().size(); i++) {
            expected.get(i).verify(analyze.getStack().get(i));
        }
    }

}
