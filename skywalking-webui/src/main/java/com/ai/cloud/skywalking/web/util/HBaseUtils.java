package com.ai.cloud.skywalking.web.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Created by xin on 16-3-21.
 */
@Repository
public class HBaseUtils {
    @Value("#{configProperties['hbaseconfig.quorum']}")
    private String quorum;
    @Value("#{configProperties['hbaseconfig.client_port']}")
    private String clientPort;

    private static Logger logger = LogManager.getLogger(HBaseUtils.class);

    private Connection connection = null;

    public Connection getConnection() {
        if (connection == null) {
            try {
                Configuration configuration = HBaseConfiguration.create();
                configuration.set("hbase.zookeeper.quorum", quorum);
                configuration.set("hbase.zookeeper.property.clientPort", clientPort);
                connection = ConnectionFactory.createConnection(configuration);
            } catch (Exception e) {
                logger.error("Create table[{}] failed", "connection hbase fail", e);
                throw new RuntimeException("Fatal error");
            }
        }

        return connection;
    }
}
