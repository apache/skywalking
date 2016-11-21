package com.a.eye.skywalking.storage.boot;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

class ElasticServer {

    private static ILog logger = LogManager.getLogger(ElasticServer.class);
    private String elasticBinDir;

    public ElasticServer(String elasticHome) {
        this.elasticBinDir = elasticHome + File.separator + "bin" + File.separator;
    }

    public void stop() throws IOException {
        int pid = readServerPID();
        if (pid == -1) {
            return;
        }

        Runtime.getRuntime().exec("kill -9 " + pid);
    }

    public boolean isStarted() throws IOException {
        return false;
    }

    public void start() throws IOException {
        Runtime.getRuntime().exec(elasticBinDir + "elasticsearch -p " + elasticBinDir + "elastic.pid -d");
    }


    private int readServerPID() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(elasticBinDir + "elastic.pid"));
            return Integer.parseInt(reader.readLine());
        } catch (IOException e) {
            logger.error("Failed to elastic server pid", e);
        }
        return -1;
    }

}
