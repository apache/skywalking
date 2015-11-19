/**
 * 
 */
package com.ai.cloud.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * @author tz
 * @date 2015年11月17日 下午5:21:59
 * @version V0.1
 */
public class HBaseConnectionUtil {

	private static Logger logger = LogManager.getLogger(HBaseConnectionUtil.class);

	private static Configuration configuration = null;
	private static Connection connection = null;

	public synchronized static Connection getConnection(){
		try {
			if (configuration == null) {
				configuration = HBaseConfiguration.create();
				configuration.set("hbase.zookeeper.quorum", Constants.QUORUM);
				configuration.set("hbase.zookeeper.property.clientPort", Constants.CLIENT_PORT);
				connection = ConnectionFactory.createConnection(configuration);
			}
		} catch (Exception e) {
			logger.error("Create table[{}] failed", "connection hbase fail", e);
		}
		return connection;
	}
}
