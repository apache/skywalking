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

package org.skywalking.apm.agent.core.logging;

import java.io.PrintStream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.skywalking.apm.agent.core.conf.Config;

/**
 * Created by wusheng on 2017/2/28.
 */
public class WriterFactoryTest {
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut() {
        errRef = System.err;
    }

    /**
     * During this test case,
     * reset {@link System#out} to a Mock object, for avoid a console system.error.
     */
    @Test
    public void testGetLogWriter() {
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setErr(mockStream);
        Assert.assertEquals(SystemOutWriter.INSTANCE, WriterFactory.getLogWriter());

        Config.Logging.DIR = "/only/test/folder";
        Assert.assertTrue(WriterFactory.getLogWriter() instanceof FileWriter);
    }

    @AfterClass
    public static void reset() {
        Config.Logging.DIR = "";
        System.setErr(errRef);
    }
}
