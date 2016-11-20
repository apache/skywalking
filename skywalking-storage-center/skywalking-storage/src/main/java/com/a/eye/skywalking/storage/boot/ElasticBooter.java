package com.a.eye.skywalking.storage.boot;

import java.io.File;

/**
 * Created by xin on 2016/11/20.
 */
public class ElasticBooter {

    private String elasticHome;

    public ElasticBooter(String elasticHome) {
        this.elasticHome = elasticHome;
    }

    public void boot(int port) {
        ElasticConfigModifier modifier = new ElasticConfigModifier(elasticHome);
        modifier.append(port).replaceConfig();

        ElasticServer elasticServer = new ElasticServer(elasticHome);

        if (elasticServer.isStarted()) {
            elasticServer.stop();
        }
        elasticServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                elasticServer.stop();
            }
        });
    }
}
