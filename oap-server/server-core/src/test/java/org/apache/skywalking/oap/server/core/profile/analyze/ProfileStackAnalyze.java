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
package org.apache.skywalking.oap.server.core.profile.analyze;

import lombok.Data;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackTree;

import java.util.List;

import static org.junit.Assert.*;

@Data
public class ProfileStackAnalyze {

    private ProfileStackData data;
    private List<ProfileStackElementMatcher> expected;

    public void analyzeAndAssert() {
        List<ProfileStack> stacks = data.transform();
        List<ProfileStackTree> trees = new ProfileAnalyzer(null, 100, 500).analyze(stacks);

        assertNotNull(trees);
        assertEquals(trees.size(), expected.size());
        for (int i = 0; i < trees.size(); i++) {
            expected.get(i).verify(trees.get(i));
        }
    }

}
