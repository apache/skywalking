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

package org.apache.skywalking.oap.server.analyzer.provider.trace.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SamplingPolicySettingsReaderTest {

    @Test
    public void testReadPolicySettings() {
        SamplingPolicySettingsReader reader = new SamplingPolicySettingsReader(this.getClass()
                                                                                   .getClassLoader()
                                                                                   .getResourceAsStream(
                                                                                       "trace-sampling-policy-settings.yml"));
        SamplingPolicySettings settings = reader.readSettings();
        assertEquals(settings.getDefaultPolicy().getRate().intValue(), 10000);
        assertEquals(settings.getDefaultPolicy().getDuration().intValue(), -1);

        assertEquals(settings.get("name1").getRate().intValue(), 1000);
        assertEquals(settings.get("name1").getDuration().intValue(), 20000);

        assertEquals(settings.get("name2").getRate().intValue(), 2000);
        assertEquals(settings.get("name2").getDuration().intValue(), 30000);
    }
}
