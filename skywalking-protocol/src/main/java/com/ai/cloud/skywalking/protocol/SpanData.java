package com.ai.cloud.skywalking.protocol;

public abstract class SpanData {
	/**
	 * Span在序列中，各字段间的分隔符
	 */
    protected static final String SPAN_FIELD_SEPARATOR = "@~";
    /**
     * Span在序列化中，新的换行符
     */
    protected static final String NEW_LINE_PLACEHOLDER = "#~";
    /**
     * 字符串中的换行符
     */
    protected static final String OS_NEW_LINE = "\n";
    /**
     * windows操作系统的多余换行字符
     */
    protected static final String WINDOWS_OS_NEW_LINE_REDUNDANT_CHAR = "\r";

    /**
     * tid，调用链的全局唯一标识
     */
    protected String traceId;
    /**
     * 当前调用链的上级描述<br/>
     * 如当前序号为：0.1.0时，parentLevel=0.1
     */
    protected String parentLevel;
    /**
     * 当前调用链的本机描述<br/>
     * 如当前序号为：0.1.0时，levelId=0
     */
    protected int levelId = 0;
    /**
     * 调用链中单个节点的入口描述<br/>
     * 如：java方法名，调用的RPC地址等等
     */
    protected String viewPointId = "";
    /**
     * 节点调用开始时间
     */
    protected long startDate = System.currentTimeMillis();
    /**
     * 节点调用花费时间
     */
    protected long cost = 0L;
    /**
     * 节点调用的发生机器描述<br/>
     * 包含机器名 + IP地址
     */
    protected String address = "";
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
     * 节点调用类型描述<br/>
     * @see com.ai.cloud.skywalking.protocol.CallType
     */
    protected String callType = "";
    /**
     * 节点分布式类型<br/>
     * 服务端/客户端
     */
    protected boolean isReceiver = false;
    /**
     * 节点调用过程中的业务字段<br/>
     * 如：业务系统设置的订单号，SQL语句等
     */
    protected String businessKey = "";
    /**
     * 节点调用的所在进程号
     */
    protected String processNo = "";
    /**
     * 节点调用所在的系统逻辑名称<br/>
     * 由授权文件指定
     */
    protected String applicationId = "";
    /**
     * 反序列化时，存储序列化前的字符串原文
     */
    protected String originData = "";
    /**
     * 用户id<br/>
     * 由授权文件指定
     */
    protected String userId;


    public String getTraceId() {
        return traceId;
    }

    public String getParentLevel() {
        return parentLevel;
    }

    public void setParentLevel(String parentLevel) {
        this.parentLevel = parentLevel;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public String getViewPointId() {
        return viewPointId;
    }

    public void setViewPointId(String viewPointId) {
        this.viewPointId = viewPointId;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSpanType() {
        return spanType;
    }

    public void setSpanType(String spanType) {
        this.spanType = spanType;
    }

    public boolean isReceiver() {
        return isReceiver;
    }

    public void setReceiver(boolean receiver) {
        isReceiver = receiver;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public void setProcessNo(String processNo) {
        this.processNo = processNo;
    }

    public String getOriginData() {
        return originData;
    }

    public long getCost() {
        return cost;
    }

    public String getAddress() {
        return address;
    }

    public byte getStatusCode() {
        return statusCode;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getProcessNo() {
        return processNo;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getCallType() {
        return callType;
    }
}
