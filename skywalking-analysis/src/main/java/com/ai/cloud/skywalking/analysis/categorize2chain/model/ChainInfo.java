package com.ai.cloud.skywalking.analysis.categorize2chain.model;

import com.ai.cloud.skywalking.analysis.categorize2chain.util.TokenGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChainInfo implements Writable {
	private String cid;
	private ChainStatus chainStatus = ChainStatus.NORMAL;
	private List<ChainNode> nodes;
	private String userId = null;
	private ChainNode firstChainNode;
	private long startDate;

	public ChainInfo(String userId) {
		super();
		this.userId = userId;
	}

	public ChainInfo() {
		this.nodes = new ArrayList<ChainNode>();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.write(new Gson().toJson(this).getBytes());
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		JsonObject jsonObject = (JsonObject) new JsonParser().parse(in
				.readLine());
		cid = jsonObject.get("cid").getAsString();
		chainStatus = ChainStatus.convert(jsonObject.get("chainStatus")
				.getAsCharacter());
		nodes = new Gson().fromJson(jsonObject.get("nodes"),
				new TypeToken<List<ChainNode>>() {
				}.getType());
		userId = jsonObject.get("userId").getAsString();
	}

	public String getCID() {
		return cid;
	}

	public String getEntranceNodeToken() {
		if (firstChainNode == null) {
			return "";
		} else {
			return firstChainNode.getNodeToken();
		}
	}

	public void generateChainToken() {
		StringBuilder chainTokenDesc = new StringBuilder();
		for (ChainNode node : nodes) {
			chainTokenDesc.append(node.getParentLevelId() + "."
					+ node.getLevelId() + "-" + node.getNodeToken() + ";");
		}
		this.cid = TokenGenerator.generateCID(chainTokenDesc.toString());
	}

	public ChainStatus getChainStatus() {
		return chainStatus;
	}

	public void setChainStatus(ChainStatus chainStatus) {
		this.chainStatus = chainStatus;
	}

	public void addNodes(ChainNode chainNode) {
		this.nodes.add(0, chainNode);
		if (chainNode.getStatus() == ChainNode.NodeStatus.ABNORMAL
				|| chainNode.getStatus() == ChainNode.NodeStatus.HUMAN_INTERRUPTION) {
			chainStatus = ChainStatus.ABNORMAL;
		}
		if (userId == null) {
			userId = chainNode.getUserId();
		}
		if ((chainNode.getParentLevelId() == null || chainNode
				.getParentLevelId().length() == 0)
				&& chainNode.getLevelId() == 0) {
			firstChainNode = chainNode;
			startDate = chainNode.getStartDate();
		}
	}

	public List<ChainNode> getNodes() {
		return nodes;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public enum ChainStatus {
		NORMAL('N'), ABNORMAL('A');
		private char value;

		ChainStatus(char value) {
			this.value = value;
		}

		public static ChainStatus convert(char value) {
			switch (value) {
			case 'N':
				return NORMAL;
			case 'A':
				return ABNORMAL;
			default:
				throw new IllegalStateException("Failed to convert[" + value
						+ "]");
			}
		}

		@Override
		public String toString() {
			return value + "";
		}
	}

	public void setCID(String cid) {
		this.cid = cid;
	}

	public long getStartDate() {
		return startDate;
	}
}
