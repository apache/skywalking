package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.List;

public class BranchTraceSpanNode extends TraceSpanNode {


    protected BranchTraceSpanNode(TraceSpanNode origin, TraceSpanNode dest,
                                  List<TraceSpanNode> spanContainer) {

        setNextBranchNode(dest);
        dest.parent = this;

        dest.setNextBranchNode(origin);
        origin.parent = this;

        this.setParent(dest.parent);
        this.branchNode = true;
        spanContainer.add(this);
    }

    public boolean hasNextBranch() {
        return nextBranchNode != null;
    }

    public TraceSpanNode nextBranch() {
        return nextBranchNode;
    }

    public void addBranch(TraceSpanNode branch) {
        TraceSpanNode lastBranchNode = null;
        while (hasNextBranch()) {
            lastBranchNode = nextBranchNode;
        }

        lastBranchNode.nextBranchNode = branch;
        branch.parent = this;
    }
}
