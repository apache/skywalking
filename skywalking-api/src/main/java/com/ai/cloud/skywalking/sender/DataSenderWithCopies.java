package com.ai.cloud.skywalking.sender;

import static com.ai.cloud.skywalking.conf.Config.Sender.MAX_COPY_NUM;

import java.util.HashSet;
import java.util.Set;

/**
 * 带副本的数据发送器
 * 
 * @author wusheng
 *
 */
public class DataSenderWithCopies implements IDataSender {
	private int maxCopyNum;

	private Set<IDataSender> senders = new HashSet<IDataSender>();

	public DataSenderWithCopies(int maxKeepConnectingSenderSize) {
		// 最大副本数量，不能大于可用最大连接数
		maxCopyNum = maxKeepConnectingSenderSize > MAX_COPY_NUM ? MAX_COPY_NUM
				: maxKeepConnectingSenderSize;
	}

	/**
	 * 尝试增加到最大可用副本数，极端情况可能不足
	 * 
	 * @param dataSender
	 * @return
	 */
	public boolean append(IDataSender dataSender) {
		// 出现重复sender，副本到达最大限度
		if (senders.contains(dataSender)) {
			return false;
		}
		senders.add(dataSender);
		return senders.size() < maxCopyNum;
	}

	/**
	 * 尝试向所有副本发送
	 */
	public boolean send(String data) {
		int successNum = 0;
		for (IDataSender sender : senders) {
			if (sender.send(data)) {
				successNum++;
			}
		}
		if (senders.size() == 1 && successNum == 1) {
			return true;
		} else if (successNum >= 2) {
			return true;
		} else {
			return false;
		}
	}

}
