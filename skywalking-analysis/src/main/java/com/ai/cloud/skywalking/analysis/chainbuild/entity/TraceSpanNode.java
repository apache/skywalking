package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.List;

import com.ai.cloud.skywalking.analysis.chainbuild.exception.TraceSpanTreeNotFountException;
import com.ai.cloud.skywalking.analysis.chainbuild.exception.TraceSpanTreeSerializeException;
import com.ai.cloud.skywalking.analysis.chainbuild.util.StringUtil;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;
import com.ai.cloud.skywalking.protocol.CallType;
import com.ai.cloud.skywalking.protocol.Span;
import com.google.gson.annotations.Expose;

public class TraceSpanNode {

    protected TraceSpanNode prev = null;


    protected TraceSpanNode next = null;


    protected TraceSpanNode parent = null;


    protected TraceSpanNode sub = null;
    @Expose
    protected String prevNodeRefToken = null;
    @Expose
    protected String nextNodeRefToken = null;
    @Expose
    protected String parentNodeRefToken = null;
    @Expose
    protected String subNodeRefToken = null;

    @Expose
    protected String nodeRefToken = null;

    @Expose
    protected boolean visualNode = true;

    @Expose
    protected String parentLevel;

    @Expose
    protected int levelId;

    @Expose
    protected String viewPointId = "";

    @Expose
    protected long cost = 0;

    @Expose
    protected long callTimes = 0;

    /**
     * 节点调用的状态<br/>
     * 0：成功<br/>
     * 1：异常<br/>
     * 异常判断原则：代码产生exception，并且此exception不在忽略列表中
     */
    @Expose
    protected byte statusCode = 0;

    /**
     * 节点调用的错误堆栈<br/>
     * 堆栈以JAVA的exception为主要判断依据
     */
    @Expose
    protected String exceptionStack;
    /**
     * 节点类型描述<br/>
     * 已字符串的形式描述<br/>
     * 如：java,dubbo等
     */
    @Expose
    protected String spanType = "";

    /**
     * 节点调用过程中的业务字段<br/>
     * 如：业务系统设置的订单号，SQL语句等
     */
    @Expose
    protected String businessKey = "";

    /**
     * 节点调用所在的系统逻辑名称<br/>
     * 由授权文件指定
     */
    @Expose
    protected String applicationId = "";

    /**
     * Warning: call this constructor ONLY by gson for deserialize
     */
    public TraceSpanNode(){
    	
    }
    
    public TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next, Span span, List<TraceSpanNode> spanContainer) {
        this(parent, sub, prev, next, spanContainer);
        this.visualNode = false;
        this.parentLevel = span.getParentLevel();
        this.levelId = span.getLevelId();
        this.viewPointId = span.getViewPointId();
        this.cost = span.getCost();
        this.callTimes = 1;
        this.statusCode = span.getStatusCode();
        if (span.isReceiver()) {
            this.exceptionStack = "server stack:";
        } else {
            this.exceptionStack = "client stack:";
        }
        this.exceptionStack += span.getExceptionStack();
        this.spanType = span.getSpanType();
        this.businessKey = span.getBusinessKey();
        this.applicationId = span.getApplicationId();

        //nodeToken : MD5(parentLevelId + levelId + viewpoint)
        nodeRefToken = TokenGenerator.generateNodeToken(parentLevel + "-" + levelId + "-" + viewPointId);

    }

    protected TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next, List<TraceSpanNode> spanContainer) {
        this.visualNode = true;
        this.setParent(parent);
        if (parent != null) {
            parent.setSub(this);
        }
        this.setSub(sub);
        if (sub != null) {
            sub.setParent(this);
        }
        this.setPrev(prev);
        if (prev != null) {
            prev.setNext(this);
        }
        this.setNext(next);
        if (next != null) {
            next.setPrev(this);
        }
        spanContainer.add(this);
    }

    protected TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next, String parentLevelId, int levelId, List<TraceSpanNode> spanContainer) {
        this(parent, sub, prev, next, spanContainer);
        this.parentLevel = parentLevelId;
        this.levelId = levelId;
        this.callTimes = 0;
    }

    boolean hasNext() {
        if (this.next != null) {
            return true;
        } else {
            return false;
        }
    }

    boolean hasSub() {
        if (this.sub != null) {
            return true;
        } else {
            return false;
        }
    }

    void mergeSpan(Span span) {
        if (CallType.convert(span.getCallType()) == CallType.ASYNC) {
            this.cost += span.getCost();
        }
        if (span.getStatusCode() != 0 && !StringUtil.isBlank(span.getExceptionStack())) {
            if (span.isReceiver()) {
                this.exceptionStack += "server stack:";
            } else {
                this.exceptionStack += "client stack:";
            }
            this.exceptionStack += span.getExceptionStack();
        }
    }

    public TraceSpanNode prev(TraceSpanTree tree) throws TraceSpanTreeNotFountException {
    	if(prev == null){
    		if(prevNodeRefToken == null){
    			throw new TraceSpanTreeNotFountException(getDesc() + " unexpected prev== null and prevNodeRefToken==null");
    		}else{
    			prev = tree.findNode(prevNodeRefToken);
    		}
    	}
        return prev;
    }

    public TraceSpanNode next(TraceSpanTree tree) throws TraceSpanTreeNotFountException {
    	if(next == null){
    		if(nextNodeRefToken == null){
    			throw new TraceSpanTreeNotFountException(getDesc() + " unexpected next== null and nextNodeRefToken==null");
    		}else{
    			next = tree.findNode(nextNodeRefToken);
    		}
    	}
        return next;
    }

    public TraceSpanNode parent(TraceSpanTree tree) throws TraceSpanTreeNotFountException {
    	if(parent == null){
    		if(parentNodeRefToken == null){
    			throw new TraceSpanTreeNotFountException(getDesc() + " unexpected parent== null and parentNodeRefToken==null");
    		}else{
    			parent = tree.findNode(parentNodeRefToken);
    		}
    	}
        return parent;
    }

    public TraceSpanNode sub(TraceSpanTree tree) throws TraceSpanTreeNotFountException {
    	if(sub == null){
    		if(subNodeRefToken == null){
    			throw new TraceSpanTreeNotFountException(getDesc() + " unexpected sub== null and subNodeRefToken==null");
    		}else{
    			sub = tree.findNode(subNodeRefToken);
    		}
    	}
        return sub;
    }

    public void setPrev(TraceSpanNode prev) {
        this.prev = prev;
    }

    public void setNext(TraceSpanNode next) {
        this.next = next;
    }

    public void setParent(TraceSpanNode parent) {
        this.parent = parent;
    }

    public void setSub(TraceSpanNode sub) {
        this.sub = sub;
    }

    public boolean isVisualNode() {
        return visualNode;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public String getViewPointId() {
        return viewPointId;
    }

    public long getCost() {
        return cost;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public String getSpanType() {
        return spanType;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getNodeRefToken() throws TraceSpanTreeSerializeException {
        if (StringUtil.isBlank(nodeRefToken)) {
            throw new TraceSpanTreeSerializeException(getDesc() + " ref token is null.");
        }
        return nodeRefToken;
    }
    
    private String getDesc(){
    	return "Node[parentLevel=" + parentLevel + ", levelId=" + levelId + ", viewPointId=" + viewPointId + "]";
    }

    void serializeRef() throws TraceSpanTreeSerializeException {
        if (prev != null) {
            prevNodeRefToken = prev.getNodeRefToken();
        }
        if (parent != null) {
            parentNodeRefToken = parent.getNodeRefToken();
        }
        if (next != null) {
            nextNodeRefToken = next.getNodeRefToken();
        }
        if (sub != null) {
            subNodeRefToken = sub.getNodeRefToken();
        }
    }
}
