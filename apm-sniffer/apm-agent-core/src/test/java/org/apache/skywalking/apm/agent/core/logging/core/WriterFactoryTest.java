package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {SnifferConfigInitializer.class, AgentPackagePath.class})
public class WriterFactoryTest {

    @Test
    public void alwaysReturnSystemLogWriteWithSetLoggingDir() {
        Config.Logging.DIR = "system.out";
        PowerMockito.mockStatic(SnifferConfigInitializer.class);
        PowerMockito.mockStatic(AgentPackagePath.class);
        BDDMockito.given(SnifferConfigInitializer.isInitCompleted()).willReturn(true);
        BDDMockito.given(AgentPackagePath.isPathFound()).willReturn(true);

        assertTrue(SnifferConfigInitializer.isInitCompleted());
        assertTrue(AgentPackagePath.isPathFound());

        IWriter logWriter = WriterFactory.getLogWriter();
        PowerMockito.verifyStatic();
        assertTrue(logWriter instanceof SystemOutWriter);
    }
}