package com.a.eye.skywalking.storage.boot;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.io.File;
import java.io.IOException;

/**
 * Created by xin on 2016/11/20.
 */
public class ElasticBootstrap {

    private static       ILog   logger                       = LogManager.getLogger(ElasticBootstrap.class);
    public static final  String DATA_INDEX_HOME              = "DATA_INDEX_HOME";
    private static final String DEVELOP_RUNTIME_ELASTIC_HOME =
            ElasticBootstrap.class.getResource("/").getPath() + ".." + File.separator + "install" + File.separator + "data"
                    + File.separator + "index";
    private String elasticHome;

    public ElasticBootstrap() {
        this.elasticHome = fetchElasticHome();
    }

    public void boot(int port) throws IOException {
        ElasticConfigModifier modifier = new ElasticConfigModifier(elasticHome);
        modifier.replaceConfig(port);

        ElasticServer elasticServer = new ElasticServer(elasticHome);

        if (elasticServer.isStarted()) {
            elasticServer.stop();
        }
        elasticServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    elasticServer.stop();
                } catch (IOException e) {
                    logger.error("Failed to stop elastic server.", e);
                }
            }
        });
    }


    public String fetchElasticHome() {
        return System.getProperty(DATA_INDEX_HOME, DEVELOP_RUNTIME_ELASTIC_HOME);
    }
}
