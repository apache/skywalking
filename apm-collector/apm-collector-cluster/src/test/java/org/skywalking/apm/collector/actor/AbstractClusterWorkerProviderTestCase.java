package org.skywalking.apm.collector.actor;

import akka.actor.ActorSystem;
import org.apache.logging.log4j.Logger;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.log.LogManager;

/**
 * @author pengys5
 */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({LogManager.class})
public class AbstractClusterWorkerProviderTestCase {

    //    @Test
    public void testOnCreate() throws ProviderNotFoundException {
        LogManager logManager = Mockito.mock(LogManager.class);
        Whitebox.setInternalState(LogManager.class, "INSTANCE", logManager);
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logManager.getFormatterLogger(Mockito.any())).thenReturn(logger);

        ActorSystem actorSystem = Mockito.mock(ActorSystem.class);
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(actorSystem);
        Impl impl = new Impl();
        impl.onCreate(null);
    }

    class Impl extends AbstractClusterWorkerProvider<AbstractClusterWorkerTestCase.Impl> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public AbstractClusterWorkerTestCase.Impl workerInstance(ClusterWorkerContext clusterContext) {
            return new AbstractClusterWorkerTestCase.Impl(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return 0;
        }
    }

    enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return AbstractClusterWorkerTestCase.Impl.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
