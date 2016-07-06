package com.ai.cloud.skywalking.reciever.processor;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.processor.exception.HBaseInitFailedException;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public abstract class AbstractSpanProcessor implements IProcessor {
    private static Logger        logger        = LogManager.getLogger(AbstractSpanProcessor.class);
    private static Configuration configuration = null;
    private static Connection connection;

    static {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            if (Config.HBaseConfig.ZK_HOSTNAME == null || "".equals(Config.HBaseConfig.ZK_HOSTNAME)) {
                logger.error("Miss HBase ZK quorum Configuration", new IllegalArgumentException("Miss HBase ZK quorum Configuration"));
                System.exit(-1);
            }
            configuration.set("hbase.zookeeper.quorum", Config.HBaseConfig.ZK_HOSTNAME);
            configuration.set("hbase.zookeeper.property.clientPort", Config.HBaseConfig.CLIENT_PORT);
        }
        try {
            connection = ConnectionFactory.createConnection(configuration);
        } catch (IOException e) {
            ServerHealthCollector.getCurrentHeathReading("hbase").updateData(ServerHeathReading.ERROR, "connect to hbase failure.");
            throw new HBaseInitFailedException("initHBaseClient failure", e);
        }
    }

    @Override
    public void process(List<AbstractDataSerializable> serializedObjects) {
        doAlarm(serializedObjects);
        doSaveHBase(connection, serializedObjects);
    }

    public abstract void doAlarm(List<AbstractDataSerializable> serializedObjects);

    public abstract void doSaveHBase(Connection connection, List<AbstractDataSerializable> serializedObjects);

}
