package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.util.StringUtil;
import com.ai.cloud.skywalking.protocol.CallType;
import com.ai.cloud.skywalking.protocol.Span;

public class TraceSpanNode {
	protected TraceSpanNode prev = null;
	
	protected TraceSpanNode next = null;
	
	protected TraceSpanNode parent = null;
	
	protected TraceSpanNode sub = null;
	
	protected boolean visualNode = true;
	
	protected String parentLevel;

	protected int levelId;

	protected String viewPointId = "";
	
	protected long cost = 0;
	
	protected long callTimes = 0;
	
    /**
     * 节点调用的状态<br/>
     * 0：成功<br/>
     * 1：异常<br/>
     * 异常判断原则：代码产生exception，并且此exception不在忽略列表中
     */
	protected byte statusCode = 0;
	
    /**
     * 节点调用的错误堆栈<br/>
     * 堆栈以JAVA的exception为主要判断依据
     */
	protected String exceptionStack;
    /**
     * 节点类型描述<br/>
     * 已字符串的形式描述<br/>
     * 如：java,dubbo等
     */
	protected String spanType = "";
	
    /**
     * 节点调用过程中的业务字段<br/>
     * 如：业务系统设置的订单号，SQL语句等
     */
	protected String businessKey = "";
    
    /**
     * 节点调用所在的系统逻辑名称<br/>
     * 由授权文件指定
     */
	protected String applicationId = ""; 
	
	public TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next, Span span) {
		this(parent, sub, prev, next);
		this.visualNode = false;
		this.parentLevel = span.getParentLevel();
		this.levelId = span.getLevelId();
		this.viewPointId = span.getViewPointId();
		this.cost = span.getCost();
		this.callTimes = 1;
		this.statusCode = span.getStatusCode();
		if(span.isReceiver()){
			this.exceptionStack = "server stack:";
		}else{
			this.exceptionStack = "client stack:";
		}
		this.exceptionStack += span.getExceptionStack();
		this.spanType = span.getSpanType();
		this.businessKey = span.getBusinessKey();
		this.applicationId = span.getApplicationId();
	}
	
	public TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next){
		this.visualNode = true;
		this.parent = parent;
		if(parent != null){
			parent.sub = this;
		}
		this.sub = sub;
		if(sub != null){
			sub.parent = this;
		}
		this.prev = prev;
		if(prev != null){
			prev.next = this;
		}
		this.next = next;
		if(next != null){
			next.prev = this;
		}
	}
	
	protected TraceSpanNode(TraceSpanNode parent, TraceSpanNode sub, TraceSpanNode prev, TraceSpanNode next, String parentLevelId, int levelId){
		this(parent, sub, prev, next);
		this.parentLevel = parentLevelId;
		this.levelId = levelId;
		this.callTimes = 0;
	}
	
	boolean hasNext(){
		if(this.next != null){
			return true;
		}else{
			return false;
		}
	}
	
	boolean hasSub(){
		if(this.sub != null){
			return true;
		}else{
			return false;
		}
	}
	
	void mergeSpan(Span span){
		if(CallType.convert(span.getCallType()) == CallType.ASYNC){
			this.cost += span.getCost(); 
		}
		if(span.getStatusCode() != 0 && !StringUtil.isBlank(span.getExceptionStack())){
			if(span.isReceiver()){
				this.exceptionStack += "server stack:";
			}else{
				this.exceptionStack += "client stack:";
			}
			this.exceptionStack += span.getExceptionStack();
		}
	}
	
	TraceSpanNode next(){
		return this.next;
	}
	
	TraceSpanNode sub(){
		return this.sub;
	}

	public TraceSpanNode getPrev() {
		return prev;
	}

	public TraceSpanNode getNext() {
		return next;
	}

	public TraceSpanNode getParent() {
		return parent;
	}

	public TraceSpanNode getSub() {
		return sub;
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
}
