package com.a.eye.skywalking.storage.boot;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class ElasticConfigModifier {

    private static ILog logger            = LogManager.getLogger(ElasticConfigModifier.class);
    private        File elasticConfigDir  = null;

    public ElasticConfigModifier(String elasticHome) {
        this.elasticConfigDir = new File(elasticHome, "config");
        if (!elasticConfigDir.exists()) {
            logger.warn("Elastic search config dir is not exists. Will create  it");
            elasticConfigDir.mkdirs();
        }
    }

    public void replaceConfig(int port) throws IOException {
        File newConfigFile = new File(elasticConfigDir, "elasticsearch.yml");
        Files.copy(ElasticConfigModifier.class.getResourceAsStream("/elasticsearch.yml"), newConfigFile.toPath(),
                REPLACE_EXISTING);
        appendingNewConfig(port, newConfigFile);
        return;
    }

    private void appendingNewConfig(int port, File newConfigFile) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(newConfigFile, true);
            writer.write("transport.tcp.port: " + port);
            writer.flush();
        } finally {
            writer.close();
        }
    }
}
