package com.ai.cloud.skywalking.analysis.model;

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChainInfo implements Writable {
    private String chainToken;
    private ChainStatus chainStatus;
    private List<ChainNode> nodes;
    private String userId;

    public ChainInfo() {
        this.nodes = new ArrayList<ChainNode>();
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.write(chainToken.getBytes());
        out.writeChar(chainStatus.getValue());

        out.writeInt(nodes.size());
        for (ChainNode chainNode : nodes) {
            out.write(chainNode.getNodeToken().getBytes());
            out.write(chainNode.getViewPoint().getBytes());
            out.writeChar(chainNode.getStatus().getValue());
            out.writeLong(chainNode.getCost());
            out.write(chainNode.getParentLevelId().getBytes());
            out.writeInt(chainNode.getLevelId());
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        chainToken = in.readLine();
        chainStatus = ChainStatus.convert(in.readChar());

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
            nodes.add(chainNode);
        }
    }


    public String getChainToken() {
        return chainToken;
    }

    public void setChainToken(String chainToken) {
        this.chainToken = chainToken;
    }

    public ChainStatus getChainStatus() {
        return chainStatus;
    }

    public void setChainStatus(ChainStatus chainStatus) {
        this.chainStatus = chainStatus;
    }

    public List<ChainNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<ChainNode> nodes) {
        this.nodes = nodes;
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
}


