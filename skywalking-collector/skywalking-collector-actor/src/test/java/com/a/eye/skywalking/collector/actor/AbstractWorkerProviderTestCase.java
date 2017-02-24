package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/24.
 */
public class AbstractWorkerProviderTestCase {


    ActorSystem system;

    @Before
    public void createSystem() {
        system = ActorSystem.create();
    }

    @After
    public void terminateSystem() throws IllegalAccessException {
        system.terminate();
        system.awaitTermination();
        system = null;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWorker(){
        AbstractWorkerProvider aWorkerProvider = new AbstractWorkerProvider() {
            @Override public String workerName() {
                return null;
            }

            @Override public Class workerClass() {
                return Object.class;
            }

            @Override public int workerNum() {
                return 1;
            }
        };

        aWorkerProvider.createWorker(system);
    }
}
