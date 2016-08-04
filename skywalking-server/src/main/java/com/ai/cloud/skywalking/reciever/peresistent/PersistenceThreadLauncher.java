package com.ai.cloud.skywalking.reciever.peresistent;

import com.ai.cloud.skywalking.reciever.conf.Config;

public class PersistenceThreadLauncher {
    public static void doLaunch() {
        if (Config.Persistence.MAX_DEAL_DATA_THREAD_NUMBER > 0) {
            new RegisterPersistenceThread().start();
            for (int i = 0; i < Config.Persistence.MAX_DEAL_DATA_THREAD_NUMBER; i++) {
                new PersistenceThread(i).start();
            }
        }
    }
}
