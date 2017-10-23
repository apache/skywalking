/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.tools;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class TimeBucketToolsTestCase {

    @Test
    public void testBuildXAxis() {
        String time = "201703250918";
        String value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.MINUTE.name(), time);
        Assert.assertEquals("09:18", value);

        value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.HOUR.name(), time);
        Assert.assertEquals("25 09", value);

        value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.DAY.name(), time);
        Assert.assertEquals("03-25", value);
    }
}
