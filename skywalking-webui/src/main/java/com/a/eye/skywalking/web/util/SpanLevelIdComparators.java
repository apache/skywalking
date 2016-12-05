package com.a.eye.skywalking.web.util;

import com.a.eye.skywalking.web.dto.FullSpan;

import java.util.Comparator;

/**
 * Created by wusheng on 2016/12/5.
 */
public class SpanLevelIdComparators {


    public static class SpanASCComparator implements Comparator<FullSpan> {
        @Override
        public int compare(FullSpan span1, FullSpan span2) {
            String span1TraceLevel = span1.getTraceLevelId();
            String span2TraceLevel = span2.getTraceLevelId();
            return ascCompare(span1TraceLevel, span2TraceLevel);
        }
    }

    public static int ascCompare(String levelId0, String levelId1) {
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
