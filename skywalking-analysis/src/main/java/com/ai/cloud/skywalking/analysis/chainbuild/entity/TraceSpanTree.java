package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.exception.BuildTraceSpanTreeException;
import com.ai.cloud.skywalking.analysis.chainbuild.exception.TraceSpanTreeNotFountException;
import com.ai.cloud.skywalking.analysis.chainbuild.exception.TraceSpanTreeSerializeException;
import com.ai.cloud.skywalking.analysis.chainbuild.util.StringUtil;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;
import com.ai.cloud.skywalking.protocol.Span;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import org.apache.hadoop.io.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class TraceSpanTree implements Writable {
    private Logger logger = LoggerFactory.getLogger(TraceSpanTree.class);

    @Expose
    private String userId = null;

    @Expose
    private String cid;

    @Expose
    private TraceSpanNode treeRoot;

    @Expose
    private List<TraceSpanNode> spanContainer = new ArrayList<TraceSpanNode>();

    private Map<String, TraceSpanNode> traceSpanNodeMap = new HashMap<String, TraceSpanNode>();

    public TraceSpanTree() {
    }

    public String build(List<Span> spanList)
            throws BuildTraceSpanTreeException, TraceSpanTreeNotFountException {
        if (spanList.size() == 0) {
            throw new BuildTraceSpanTreeException("spanList is empty.");
        }

        Collections.sort(spanList, new Comparator<Span>() {
            @Override
            public int compare(Span span1, Span span2) {
                String span1TraceLevel = span1.getParentLevel() + "."
                        + span1.getLevelId();
                String span2TraceLevel = span2.getParentLevel() + "."
                        + span2.getLevelId();
                return span1TraceLevel.compareTo(span2TraceLevel);
            }
        });
        Span span = spanList.get(0);
        if (!StringUtil.isBlank(span.getUserId())) {
            userId = span.getUserId();
        } else {
            throw new BuildTraceSpanTreeException(
                    "spanList[0] 's userId is null");
        }
        cid = generateCID(spanList.get(0));
        treeRoot = new TraceSpanNode(null, null, null, null, spanList.get(0),
                spanContainer);
        if (spanList.size() > 1) {
            for (int i = 1; i < spanList.size(); i++) {
                this.build(spanList.get(i));
            }
        }

        return cid;
    }

    private void build(Span span) throws BuildTraceSpanTreeException,
            TraceSpanTreeNotFountException {
        if (userId == null && !StringUtil.isBlank(span.getUserId())) {
            userId = span.getUserId();
        }

        TraceSpanNode clientOrServerNode = findNodeAndCreateVisualNodeIfNess(
                span.getParentLevel(), span.getLevelId());
        if (clientOrServerNode != null) {
            clientOrServerNode.mergeSpan(span);
        }

        if (span.getLevelId() > 0) {
            TraceSpanNode foundNode = findNodeAndCreateVisualNodeIfNess(
                    span.getParentLevel(), span.getLevelId() - 1);
            /**
             * Create node between foundNode and foundNode.next(maybe
             * foundNode.next == null)
             */
            new TraceSpanNode(null, null, foundNode, foundNode.next(this),
                    span, spanContainer);
        } else {
            /**
             * levelId=0 find for parent level if parentLevelId = 0.0.1 then
             * find node[parentLevelId=0.0,levelId=1]
             */
            String parentLevel = span.getParentLevel();
            int idx = parentLevel.lastIndexOf("\\.");
            if (idx < 0) {
                throw new BuildTraceSpanTreeException("parentLevel="
                        + parentLevel + " is unexpected.");
            }
            TraceSpanNode foundNode = findNodeAndCreateVisualNodeIfNess(
                    parentLevel.substring(0, idx),
                    Integer.parseInt(parentLevel.substring(idx + 1)));
            /**
             * Create sub node of using span data. FoundNode is parent node.
             */
            new TraceSpanNode(foundNode, null, null, null, span, spanContainer);

        }
    }

    private TraceSpanNode findNodeAndCreateVisualNodeIfNess(
            String parentLevelId, int levelId)
            throws TraceSpanTreeNotFountException {
        String levelDesc = StringUtil.isBlank(parentLevelId) ? (levelId + "")
                : (parentLevelId + "." + levelId);
        String[] levelArray = levelDesc.split("\\.");

        TraceSpanNode currentNode = treeRoot;
        String contextParentLevelId = "";
        for (String currentLevel : levelArray) {
            int currentLevelInt = Integer.parseInt(currentLevel);
            for (int i = 0; i < currentLevelInt; i++) {
                if (currentNode.hasNext()) {
                    currentNode = currentNode.next(this);
                } else {
                    // create visual next node
                    currentNode = new VisualTraceSpanNode(null, null,
                            currentNode, null, contextParentLevelId, i,
                            spanContainer);
                }
            }
            contextParentLevelId = contextParentLevelId == "" ? ("" + currentLevelInt)
                    : (contextParentLevelId + "." + currentLevelInt);
            if (currentNode.hasSub()) {
                currentNode = currentNode.sub(this);
            } else {
                // create visual sub node
                currentNode = new VisualTraceSpanNode(currentNode, null, null,
                        null, contextParentLevelId, 0, spanContainer);
            }
        }

        return currentNode;
    }

    private String generateCID(Span level0Span)
            throws BuildTraceSpanTreeException {
        if (StringUtil.isBlank(level0Span.getParentLevel())
                && level0Span.getLevelId() == 0) {
            StringBuilder chainTokenDesc = new StringBuilder();
            chainTokenDesc.append(userId).append("_");
            chainTokenDesc.append(level0Span.getViewPointId());
            return getTSBySpanTraceId(level0Span) + "_" + TokenGenerator.generateCID(chainTokenDesc.toString());
        } else {
            throw new BuildTraceSpanTreeException("tid:"
                    + level0Span.getTraceId() + " level0 span data is illegal");
        }
    }

    private static String getTSBySpanTraceId(Span span)
            throws BuildTraceSpanTreeException {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(Long.parseLong(span.getTraceId().split(
                    "\\.")[2])));
            return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1);
        } catch (Throwable t) {
            throw new BuildTraceSpanTreeException("tid:" + span.getTraceId()
                    + " is illegal.");
        }
    }

    private void beforeSerialize() throws TraceSpanTreeSerializeException {
        for (TraceSpanNode treeNode : spanContainer) {
            treeNode.serializeRef();
        }
    }

    public String serialize() throws TraceSpanTreeSerializeException {
        beforeSerialize();
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
                .create().toJson(this);
    }

    TraceSpanNode findNode(String nodeRefToken)
            throws TraceSpanTreeNotFountException {
        if (traceSpanNodeMap.containsKey(nodeRefToken)) {
            return traceSpanNodeMap.get(nodeRefToken);
        } else {
            throw new TraceSpanTreeNotFountException("nodeRefToken="
                    + nodeRefToken + " not found.");
        }
    }

    @Override
    public void write(DataOutput out) throws IOException {
        try {
            out.write(serialize().getBytes());
        } catch (TraceSpanTreeSerializeException e) {
            logger.error("Failed to serialize Chain Id[" + cid + "]", e);
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        String value = in.readLine();
        try {
            JsonObject jsonObject = (JsonObject) new JsonParser().parse(value);
            userId = jsonObject.get("userId").getAsString();
            cid = jsonObject.get("cid").getAsString();
            treeRoot = new Gson().fromJson(jsonObject.get("treeRoot"),
                    TraceSpanNode.class);
            spanContainer = new Gson().fromJson(
                    jsonObject.get("spanContainer"),
                    new TypeToken<List<TraceSpanNode>>() {
                    }.getType());
            for (TraceSpanNode node : spanContainer) {
                traceSpanNodeMap.put(node.getNodeRefToken(), node);
            }
        } catch (Exception e) {
            logger.error("Failed to parse the value[" + value
                    + "] to TraceSpanTree Object", e);
        }
    }

    public TraceSpanNode getTreeRoot() {
        return treeRoot;
    }

    public String getCid() {
        return cid;
    }

    public void merge(TraceSpanTree spanTree) {
        if (spanTree.getTreeRoot().hasNext()) {
            SpanTreeMerger.merge(spanTree.getTreeRoot().next, treeRoot.next, spanContainer);
        }

        if (spanTree.getTreeRoot().hasSub()) {
            SpanTreeMerger.merge(spanTree.getTreeRoot().sub, treeRoot.sub, spanContainer);
        }
    }

    private static class SpanTreeMerger {
        public static boolean merge(TraceSpanNode origin, TraceSpanNode dest, List<TraceSpanNode> spanContainer) {
            boolean flag = false;
            if (origin == null || dest == null) {
                if (origin != null && dest == null) {
                    dest.parent.sub = origin;
                    origin.parent = dest.parent;
                    return true;
                }
                return true;
            }

            if (dest.isBranchNode()) {
                BranchTraceSpanNode branchTraceSpanNode = (BranchTraceSpanNode) dest;
                boolean branchFlag = false;
                while (branchTraceSpanNode.hasNextBranch()) {
                    branchFlag = merge(origin, branchTraceSpanNode.nextBranch(), spanContainer);
                    if (branchFlag) {
                        break;
                    }
                }

                if (branchFlag) {
                    return true;
                } else {
                    branchTraceSpanNode.addBranch(origin);
                    return false;
                }
            }

            if (origin.isVisualNode() || dest.isVisualNode()) {
                boolean nextFlag = merge(origin.next, dest.next, spanContainer);
                boolean subFlag = merge(origin.sub, dest.sub, spanContainer);

                if (subFlag && nextFlag) {
                    // 合并子树数据
                } else {
                    new BranchTraceSpanNode(origin, dest, spanContainer);
                }
                flag = nextFlag && nextFlag;
            } else {
                if (origin.nodeRefToken.equals(dest.nodeRefToken)) {
                    // 合并子树数据
                    flag = true;
                } else {
                    new BranchTraceSpanNode(origin, dest, spanContainer);
                    flag = false;
                }
                flag = flag && merge(origin.next, dest.next, spanContainer);
                flag = flag && merge(origin.sub, dest.sub, spanContainer);
            }

            return flag;
        }
    }
}
