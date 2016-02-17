package com.ai.cloud.skywalking.reciever.persistance;

import com.ai.cloud.skywalking.reciever.conf.Config;

public class PersistenceThreadLauncher {

    public static void doLaunch() {
        new RegisterPersistenceThread().start();
        for (int i = 0; i < Config.Server.MAX_DEAL_DATA_THREAD_NUMBER; i++) {
            new PersistenceThread(i).start();
        }
    }
}
