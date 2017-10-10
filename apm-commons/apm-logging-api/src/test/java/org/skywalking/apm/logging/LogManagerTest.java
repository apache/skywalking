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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

/**
 * @author wusheng
 */
public class LogManagerTest {

    @Test
    public void testGetNoopLogger() {
        ILog logger = LogManager.getLogger(LogManagerTest.class);
        Assert.assertEquals(NoopLogger.INSTANCE, logger);
    }

    @Before
    @After
    public void clear() throws IllegalAccessException {
        MemberModifier.field(LogManager.class, "RESOLVER").set(null, null);
    }

    public class TestLogger implements ILog {

        @Override
        public void info(String format) {

        }

        @Override
        public void info(String format, Object... arguments) {

        }

        @Override
        public void warn(String format, Object... arguments) {

        }

        @Override
        public void error(String format, Throwable e) {

        }

        @Override
        public void error(Throwable e, String format, Object... arguments) {

        }

        @Override
        public boolean isDebugEnable() {
            return false;
        }

        @Override
        public boolean isInfoEnable() {
            return false;
        }

        @Override
        public boolean isWarnEnable() {
            return false;
        }

        @Override
        public boolean isErrorEnable() {
            return false;
        }

        @Override
        public void debug(String format) {

        }

        @Override
        public void debug(String format, Object... arguments) {

        }

        @Override
        public void error(String format) {

        }
    }

}
