package com.a.eye.skywalking.sender;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.selfexamination.HeathReading;
import com.a.eye.skywalking.selfexamination.SDKHealthCollector;
import com.a.eye.skywalking.protocol.common.ISerializable;

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
		maxCopyNum = maxKeepConnectingSenderSize > Config.Sender.MAX_COPY_NUM ? Config.Sender.MAX_COPY_NUM
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

	boolean isReady(){
		return senders.size() > 0 ;
	}


	/**
	 * 尝试向所有副本发送
	 */
	public boolean send(List<ISerializable> packageData) {
		int successNum = 0;
		for (IDataSender sender : senders) {
			if (sender.send(packageData)) {
				successNum++;
			}
		}
		SDKHealthCollector.getCurrentHeathReading("DataSenderWithCopies").updateData(HeathReading.INFO, "DataSender send data with copynum=" + successNum + " successfully.");
		if (senders.size() == 1 && successNum == 1) {
			return true;
		} else if (successNum >= 2) {
			return true;
		} else {
			return false;
		}
	}

}
