package test.com.ai.skywalking.test.api;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TraceTreeAssert {

    public static void assertEquals(String[][] expectedTraceTree) {
        List<Span> spanDataInBuffer = TraceTreeDataAcquirer.acquireCurrentTraceSpanData();

        validateTraceId(spanDataInBuffer);

        Set<String> assertSpanData = distinctAndConvertSpanData(spanDataInBuffer);
        Set<String> expectedSpanData = distinctAndConvertSpanData(expectedTraceTree);

        validateTraceSpanSize(expectedSpanData.size(), assertSpanData.size());
        validateSpanData(expectedSpanData, assertSpanData);
    }

    private static Set<String> distinctAndConvertSpanData(List<Span> assertSpanData) {
        Set<String> resultSpanData = new HashSet<>();
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

    private static Set<String> distinctAndConvertSpanData(String[][] assertTraceTree) {
        Set<String> resultSpanData = new HashSet<String>();
        for (String[] spanDataArray : assertTraceTree) {
            if (spanDataArray.length != 4) {
                throw new IllegalArgumentException("assert trace tree is illegal, " +
                        "Format :\tParentLevelId\t|\tlevelId\t|\tviewPoint\t|\tbusinesskey");
            }

            StringBuffer tmpSpanDataStr = new StringBuffer(jointTraceLevelId(spanDataArray[0], spanDataArray[1]) + " ");
            tmpSpanDataStr.append(spanDataArray[2] == null ? " " : spanDataArray[2].trim() + " ")
                    .append(spanDataArray[3] == null ? " " : spanDataArray[3].trim() + " ");

            resultSpanData.add(tmpSpanDataStr.toString());
        }

        return resultSpanData;
    }


    private static void validateTraceSpanSize(int actualSpanSize, int expectedSpanSize) {
        if (actualSpanSize != expectedSpanSize) {
            throw new RuntimeException("expected span size : " + expectedSpanSize +
                    "\n actual span size : " + actualSpanSize);
        }
    }

    private static void validateSpanData(Set<String> expectedSpanData, Set<String> assertTraceTree) {
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

            throw new RuntimeException("actual trace tree is not contain those span as follow:\n" + stringBuffer);
        }

    }

    private static void validateTraceId(List<Span> traceSpanList) {
        String traceId = null;
        for (Span span : traceSpanList) {
            if (traceId == null) {
                traceId = span.getTraceId();
            }

            if (!traceId.equals(span.getTraceId())) {
                throw new RuntimeException("trace id is not all the same.trace id :" +
                        traceId + ",Error trace id :" + span.getTraceId());
            }

        }
    }
}
