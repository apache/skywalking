/**
 * 
 */
package com.ai.cloud.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.vo.mvo.BuriedPointEntry;

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

	static {
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
	}

	public static List<BuriedPointEntry> selectByTraceId(String traceId) throws IOException {
		List<BuriedPointEntry> entries = new ArrayList<BuriedPointEntry>();
		Table table = connection.getTable(TableName.valueOf(Constants.TABLE_NAME_CHAIN));
		Get g = new Get(Bytes.toBytes(traceId));
		Result r = table.get(g);
		for (Cell cell : r.listCells()) {
			if (cell.getValueArray().length > 0)
				entries.add(BuriedPointEntry.convert(new String(cell.getValue(), "UTF-8")));
		}
		return entries;
	}

	/**
	 * @throws IOException
	 * 
	 */
	public static void main(String[] args) throws IOException {
		List<BuriedPointEntry> bpe = HBaseConnectionUtil.selectByTraceId("08388950294948ef91e30e70c4ec183e123");

		for (BuriedPointEntry tmpVO : bpe) {
			logger.info(tmpVO);
		}
	}
}
