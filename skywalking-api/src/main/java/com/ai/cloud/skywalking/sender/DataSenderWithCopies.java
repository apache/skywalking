package com.ai.cloud.skywalking.sender;

import static com.ai.cloud.skywalking.conf.Config.Sender.MAX_COPY_NUM;

import java.util.ArrayList;
import java.util.List;

/**
 * 带副本的数据发送器
 * @author wusheng
 *
 */
public class DataSenderWithCopies implements IDataSender{
	private int maxCopyNum;
	
	private List<IDataSender> senders = new ArrayList<IDataSender>();
	
	public DataSenderWithCopies(int maxKeepConnectingSenderSize){
		//最大副本数量，不能大于可用最大连接数
		maxCopyNum = maxKeepConnectingSenderSize > MAX_COPY_NUM ? MAX_COPY_NUM: maxKeepConnectingSenderSize;
	}
	
	/**
	 * 尝试增加到最大可用副本数，极端情况可能不足
	 * @param dataSender
	 * @return
	 */
	public boolean append(IDataSender dataSender){
		senders.add(dataSender);
		return senders.size() < maxCopyNum;
	}

	/**
	 * 尝试向所有副本发送
	 */
	public boolean send(String data) {
		int successNum = 0;
		for(IDataSender sender : senders){
			if(sender.send(data)){
				successNum++;
			}
		}
		if(successNum >= 2 || successNum >= maxCopyNum){
			return true;
		}else{
			return false;
		}
	}

}
