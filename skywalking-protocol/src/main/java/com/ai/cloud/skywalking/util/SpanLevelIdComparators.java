package com.ai.cloud.skywalking.util;


import com.ai.cloud.skywalking.protocol.Span;

import java.util.Comparator;

public class SpanLevelIdComparators {

    public static class SpanDESCComparator implements Comparator<Span> {
        @Override
        public int compare(Span span1, Span span2) {
            String span1TraceLevel = getTraceLevelId(span1);
            String span2TraceLevel = getTraceLevelId(span2);
            return descComparator(span1TraceLevel, span2TraceLevel);
        }
    }

    public static class SpanASCComparator implements Comparator<Span> {
        @Override
        public int compare(Span span1, Span span2) {
            String span1TraceLevel = getTraceLevelId(span1);
            String span2TraceLevel = getTraceLevelId(span2);
            return ascCompare(span1TraceLevel, span2TraceLevel);
        }
    }

    private static String getTraceLevelId(Span span) {
        String spanTraceLevelId = null;
        if (span.getParentLevel() == null || span.getParentLevel().length() == 0) {
            spanTraceLevelId = span.getLevelId() + "";
        } else {
            spanTraceLevelId = span.getParentLevel() + "." + span.getLevelId();
        }
        return spanTraceLevelId;
    }

    private static int descComparator(String levelId0, String levelId1) {
        String[] levelId0Array = levelId0.split("\\.");
        String[] levelId1Array = levelId1.split("\\.");
        int result = -1;
        int index = 0;
        while (true) {
            if (index >= levelId0Array.length) {
                result = 1;
                break;
            }

            if (index >= levelId1Array.length) {
                result = -1;
                break;
            }
            result = -1 * new Integer(levelId0Array[index]).compareTo(new Integer(levelId1Array[index]));
            if (result != 0)
                break;
            index++;

        }
        return result;
    }


    private static int ascCompare(String levelId0, String levelId1) {
        String[] levelId0Array = levelId0.split("\\.");
        String[] levelId1Array = levelId1.split("\\.");
        int result = -1;
        int index = 0;
        while (true) {
            if (index >= levelId0Array.length) {
                result = -1;
                break;
            }

            if (index >= levelId1Array.length) {
                result = 1;
                break;
            }
            result = new Integer(levelId0Array[index]).compareTo(new Integer(levelId1Array[index]));
            if (result != 0)
                break;
            index++;

        }
        return result;
    }
}
