package com.ai.cloud.skywalking.analysis.chainbuild.po;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.client.Put;

import com.ai.cloud.skywalking.analysis.chainbuild.exception.Tid2CidECovertException;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;

public class ChainInfo implements Serializable {
	private static final long serialVersionUID = -7194044877533469817L;
	
	/**
	 * 0节点的viewpoint，用于明文标识入口
	 */
	private String callEntrance;
    private String cid;
    private ChainStatus chainStatus = ChainStatus.NORMAL;
    private List<ChainNode> nodes = new ArrayList<ChainNode>();
    private String userId = null;
    private ChainNode firstChainNode;
    private long startDate;
    private String tid;

    public ChainInfo(String tid) {
        super();
        this.tid = tid;
    }

    public String getCID() {
        return cid;
    }

    public String getEntranceNodeToken() throws Tid2CidECovertException {
        if (firstChainNode == null) {
        	throw new Tid2CidECovertException("tid[" + tid + "] can't find span node with level=0.");
        } else {
            return this.getUserId() + ":"
					+ firstChainNode.getNodeToken();
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
            callEntrance = firstChainNode.getViewPoint();
        }
    }

    public List<ChainNode> getNodes() {
        return nodes;
    }

    public String getUserId() {
        return userId;
    }

    public void saveToHBase(Put put) {
    	//TODO： @zhangxin，未完成的入库代码
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

    public String getCallEntrance() {
        return callEntrance;
    }
}
