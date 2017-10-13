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
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.logging;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/27.
 */
public class NoopLoggerTest {
    @Test
    public void testOnNothing() {
        Assert.assertFalse(NoopLogger.INSTANCE.isDebugEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isInfoEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isErrorEnable());
        Assert.assertFalse(NoopLogger.INSTANCE.isWarnEnable());

        NoopLogger.INSTANCE.debug("Any string");
        NoopLogger.INSTANCE.debug("Any string", new Object[0]);
        NoopLogger.INSTANCE.info("Any string");
        NoopLogger.INSTANCE.info("Any string", new Object[0]);
        NoopLogger.INSTANCE.warn("Any string", new Object[0]);
        NoopLogger.INSTANCE.warn("Any string", new Object[0], new NullPointerException());
        NoopLogger.INSTANCE.error("Any string");
        NoopLogger.INSTANCE.error("Any string", new NullPointerException());
    }
}
