package com.ai.skywalking.testframework.api;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.skywalking.testframework.api.exception.SpanDataFormatException;
import com.ai.skywalking.testframework.api.exception.SpanDataNotEqualsException;
import com.ai.skywalking.testframework.api.exception.TraceIdNotSameException;
import com.ai.skywalking.testframework.api.exception.TraceNodeSizeNotEqualException;

import java.util.ArrayList;
import java.util.List;

public class TraceTreeAssert {

    public static void assertEquals(String[][] expectedTraceTree) {
        List<Span> spanDataInBuffer = TraceTreeDataAcquirer.acquireCurrentTraceSpanData();

        validateTraceId(spanDataInBuffer);

        List<String> assertSpanData = convertSpanDataToCompareStr(spanDataInBuffer);

        List<String> expectedSpanData = convertSpanDataToCompareStr(expectedTraceTree);

        validateTraceSpanSize(expectedSpanData.size(), assertSpanData.size());

        validateSpanData(expectedSpanData, assertSpanData);
    }

    private static List<String> convertSpanDataToCompareStr(List<Span> assertSpanData) {
        List<String> resultSpanData = new ArrayList<String>();
        for (Span span : assertSpanData) {
            StringBuffer tmpSpanDataStr = new StringBuffer(jointTraceLevelId(span.getParentLevel(), span.getLevelId() + " "));
            tmpSpanDataStr.append(span.getViewPointId().trim() + " ")
                    .append(span.getBusinessKey().trim() + " ");

            resultSpanData.add(tmpSpanDataStr.toString());
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
                throw new SpanDataFormatException("assert trace tree is illegal, " +
                        "Format :\ttraceLevelId\t|\tviewPoint\t|\tbusinesskey");
            }

            StringBuffer tmpSpanDataStr = new StringBuffer(spanDataArray[0] + " ");
            tmpSpanDataStr.append(spanDataArray[1] == null ? " " : spanDataArray[1].trim() + " ")
                    .append(spanDataArray[2] == null ? " " : spanDataArray[2].trim() + " ");

            resultSpanData.add(tmpSpanDataStr.toString());
        }

        return resultSpanData;
    }


    private static void validateTraceSpanSize(int actualSpanSize, int expectedSpanSize) {
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

    private static void validateTraceId(List<Span> traceSpanList) {
        String traceId = null;
        for (Span span : traceSpanList) {
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
