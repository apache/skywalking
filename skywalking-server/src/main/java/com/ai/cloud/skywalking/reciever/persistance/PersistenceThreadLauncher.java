package com.ai.cloud.skywalking.reciever.persistance;

import com.ai.cloud.skywalking.reciever.conf.Config;

public class PersistenceThreadLauncher {

    public static void doLaunch() {
        new RegisterPersistenceThread().start();
        for (int i = 0; i < Config.Persistence.MAX_THREAD_NUMBER; i++) {
            new PersistenceThread().start();
        }
    }
}
