package com.ai.cloud.skywalking.analysis.model;

import com.ai.cloud.skywalking.analysis.util.TokenGenerator;
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
        out.write(cid.getBytes());
        out.writeChar(chainStatus.getValue());
        out.write(userId.getBytes());

        out.writeInt(nodes.size());
        for (ChainNode chainNode : nodes) {
            out.write(chainNode.getNodeToken().getBytes());
            out.write(chainNode.getViewPoint().getBytes());
            out.writeChar(chainNode.getStatus().getValue());
            out.writeLong(chainNode.getCost());
            out.write(chainNode.getParentLevelId().getBytes());
            out.writeInt(chainNode.getLevelId());
            out.write(chainNode.getBusinessKey().getBytes());
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
    	cid = in.readLine();
        chainStatus = ChainStatus.convert(in.readChar());
        userId = in.readLine();

        int nodeSize = in.readInt();
        this.nodes = new ArrayList<ChainNode>();
        for (int i = 0; i < nodeSize; i++) {
            ChainNode chainNode = new ChainNode();
            chainNode.setNodeToken(in.readLine());
            chainNode.setViewPoint(in.readLine());
            chainNode.setStatus(ChainNode.NodeStatus.convert(in.readChar()));
            chainNode.setCost(in.readLong());
            chainNode.setParentLevelId(in.readLine());
            chainNode.setLevelId(in.readInt());
            chainNode.setBusinessKey(in.readLine());

            nodes.add(chainNode);
        }
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
            chainTokenDesc.append(node.getParentLevelId() + "." + node.getLevelId() + "-" + node.getNodeToken() + ";");
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
        if (chainNode.getStatus() == ChainNode.NodeStatus.ABNORMAL) {
            chainStatus = ChainStatus.ABNORMAL;
        }
        if (userId == null) {
            userId = chainNode.getUserId();
        }
        if ((chainNode.getParentLevelId() == null || chainNode.getParentLevelId().length() == 0)
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

        public char getValue() {
            return value;
        }

        public static ChainStatus convert(char value) {
            switch (value) {
                case 'N':
                    return NORMAL;
                case 'A':
                    return ABNORMAL;
                default:
                    throw new IllegalStateException("Failed to convert[" + value + "]");
            }
        }
    }

    public void setCID(String cid) {
        this.cid = cid;
    }

    public long getStartDate() {
        return startDate;
    }
}


