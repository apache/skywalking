package com.a.eye.skywalking.testframework.api;

import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.common.ISerializable;
import com.a.eye.skywalking.testframework.api.exception.SpanDataNotEqualsException;
import com.a.eye.skywalking.testframework.api.exception.SpanDataFormatException;
import com.a.eye.skywalking.testframework.api.exception.TraceIdNotSameException;
import com.a.eye.skywalking.testframework.api.exception.TraceNodeSizeNotEqualException;

import java.util.ArrayList;
import java.util.List;

public class RequestSpanAssert {

    public static void assertEquals(String[][] expectedRequestSpan) {
        assertEquals(expectedRequestSpan, false);
    }

    public static void assertEquals(String[][] expectedRequestSpan, boolean skipValidateTraceId) {
        List<RequestSpan> requestSpan = acquiredRequestSpanFromBuffer();

        if (!skipValidateTraceId) {
            validateTraceId(requestSpan);
        }

        List<String> assertSpanData = convertSpanDataToCompareStr(requestSpan);

        List<String> expectedSpanData = convertSpanDataToCompareStr(expectedRequestSpan);

        validateTraceSpanSize(expectedSpanData.size(), assertSpanData.size());

        validateSpanData(expectedSpanData, assertSpanData);

    }

    private static List<RequestSpan> acquiredRequestSpanFromBuffer() {
        List<ISerializable> spans = ContextPoolOperator.acquireBufferData();

        List<RequestSpan> result = new ArrayList<RequestSpan>();
        for (ISerializable span : spans) {
            if (span instanceof RequestSpan) {
                result.add((RequestSpan) span);
            }
        }
        return result;
    }

    public static void clearTraceData() {
        ContextPoolOperator.clearSpanData();
    }

    private static List<String> convertSpanDataToCompareStr(List<RequestSpan> assertSpanData) {
        List<String> resultSpanData = new ArrayList<String>();
        for (RequestSpan span : assertSpanData) {
            StringBuffer tmpSpanDataStr = new StringBuffer(jointTraceLevelId(span.getParentLevel(), span.getLevelId() + " "));
            tmpSpanDataStr.append(span.getViewPointId().trim() + " ");
            tmpSpanDataStr.append(span.getBusinessKey() == null ? " " : span.getBusinessKey() + " ");

            resultSpanData.add(tmpSpanDataStr.toString().trim());
        }
        return resultSpanData;
    }

    private static String jointTraceLevelId(String parentLevelId, String levelId) {
        String traceLevelId = "";
        if (parentLevelId != null && parentLevelId.length() > 0) {
            traceLevelId = parentLevelId + ".";
        }
        traceLevelId += levelId;
        return traceLevelId;
    }

    private static List<String> convertSpanDataToCompareStr(String[][] assertTraceTree) {
        List<String> resultSpanData = new ArrayList<String>();
        for (String[] spanDataArray : assertTraceTree) {
            if (spanDataArray.length != 3) {
                throw new SpanDataFormatException("assert trace tree is illegal, " + "Format :\ttraceLevelId\t|\tviewPoint\t|\tbusinesskey");
            }

            StringBuffer tmpSpanDataStr = new StringBuffer(spanDataArray[0] + " ");
            tmpSpanDataStr.append(spanDataArray[1] == null ? " " : spanDataArray[1].trim() + " ").append(spanDataArray[2] == null ? " " : spanDataArray[2].trim() + " ");

            resultSpanData.add(tmpSpanDataStr.toString().trim());
        }

        return resultSpanData;
    }


    private static void validateTraceSpanSize(int expectedSpanSize, int actualSpanSize) {
        if (actualSpanSize != expectedSpanSize) {
            throw new TraceNodeSizeNotEqualException("expected span size : " + expectedSpanSize +
                    "\n actual span size : " + actualSpanSize);
        }
    }

    private static void validateSpanData(List<String> expectedSpanData, List<String> assertTraceTree) {
        for (String assertSpanDataStr : assertTraceTree) {
            if (expectedSpanData.contains(assertSpanDataStr)) {
                expectedSpanData.remove(assertSpanDataStr);
            }
        }

        if (expectedSpanData.size() != 0) {
            StringBuffer stringBuffer = new StringBuffer();
            for (String expectedSpan : expectedSpanData) {
                stringBuffer.append(expectedSpan + "\n");
            }

            throw new SpanDataNotEqualsException("actual trace tree is not contain those span as follow:\n" + stringBuffer);
        }

    }

    private static void validateTraceId(List<RequestSpan> traceSpanList) {
        String traceId = null;
        for (RequestSpan span : traceSpanList) {
            if (traceId == null) {
                traceId = span.getTraceId();
            }

            if (!traceId.equals(span.getTraceId())) {
                throw new TraceIdNotSameException("trace id is not all the same.trace id :" +
                        traceId + ",Error trace id :" + span.getTraceId());
            }

        }
    }
}
