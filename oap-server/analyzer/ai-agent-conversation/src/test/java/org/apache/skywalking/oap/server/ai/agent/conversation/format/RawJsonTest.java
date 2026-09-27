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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RawJsonTest {
    /**
     * Each part's data is the text the line holds, a literal null included, and null for a part without data.
     */
    @Test
    public void partDataIsTheTextTheLineHolds() {
        assertEquals(Arrays.asList("{\"z\": 1, \"a\":[ 1,2 ]}", "null", null),
                     RawJson.partData("{\"ord\":1,\"parts\":[{\"k\":\"data\",\"data\":{\"z\": 1, \"a\":[ 1,2 ]}},"
                         + "{\"k\":\"result\",\"data\":null},{\"k\":\"text\",\"text\":\"t\"}]}"));
    }

    /**
     * A line that is not the JSON the walk expects gives no data and always ends: a value is at least one character,
     * so the walk never stands still.
     */
    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void aLineThatIsNotJsonGivesNoData() {
        assertEquals(Collections.emptyList(), RawJson.partData("{\"parts\":[{\"data\":[1}]}]}"));
        assertEquals(Collections.emptyList(), RawJson.partData("{\"parts\":[{\"data\":[,]}]}"));
    }
}
