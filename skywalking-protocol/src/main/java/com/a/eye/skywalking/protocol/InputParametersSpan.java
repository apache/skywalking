package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.protocol.proto.TraceProtocol;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;

public class InputParametersSpan extends AbstractDataSerializable {

    private static final InputParametersSpan INSTANCE = new InputParametersSpan();

    private static int parameterIndex = 0;
    /**
     * tid，调用链的全局唯一标识
     */
    private String traceId;
    /**
     * 当前调用链的描述<br/>
     */
    private String traceLevelId;

    /**
     * 埋点入参列表,补充时触发
     */
    private Map<String, String> parameters = new HashMap<String, String>();


    public InputParametersSpan() {
    }

    public InputParametersSpan(String traceId, String traceLevelId) {
        this.traceLevelId = traceLevelId;
        this.traceId = traceId;
    }

    public int getDataType() {
        return 3;
    }

    public byte[] getData() {
        TraceProtocol.InputParametersSpan.Builder builder =
                TraceProtocol.InputParametersSpan.newBuilder().setTraceId(traceId).setTraceLevelId(traceLevelId);

        if (parameters != null && parameters.size() > 0) {
            builder.putAllParameters(parameters);
        }

        return builder.build().toByteArray();
    }

    public AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException {
        InputParametersSpan result = new InputParametersSpan();
        try {
            TraceProtocol.InputParametersSpan parametersSpan = TraceProtocol.InputParametersSpan.parseFrom(data);
            result.traceId = parametersSpan.getTraceId();
            result.traceLevelId = parametersSpan.getTraceLevelId();
            result.parameters = parametersSpan.getParametersMap();
        } catch (InvalidProtocolBufferException e) {
            throw new ConvertFailedException("Failed to convert to parametersSpan", e);
        }
        return result;
    }

    public static InputParametersSpan convert(byte[] data) throws ConvertFailedException {
        return (InputParametersSpan) INSTANCE.convertData(data);
    }

    public boolean isNull() {
        return false;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }


    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void setTraceLevelId(String traceLevelId) {
        this.traceLevelId = traceLevelId;
    }


    public void addParameter(String parameter) {
        parameters.put("_" + parameterIndex, parameter);
        parameterIndex++;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
