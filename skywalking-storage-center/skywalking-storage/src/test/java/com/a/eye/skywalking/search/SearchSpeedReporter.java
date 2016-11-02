package com.a.eye.skywalking.search;

import java.util.Arrays;
import java.util.TreeSet;

public class SearchSpeedReporter {

    private static final long   BASE_TIME_STAMP = 1477983548L;
    private static       Long[] testedData      = new Long[3000];

    private static TreeSet<Long> tree = new TreeSet<Long>();

    public static void initData() {
        for (int i = 0; i < 3000; i++) {
            testedData[i] = new Long(BASE_TIME_STAMP + i * 1000 * 60 * 60L);
        }
        tree.addAll(Arrays.<Long>asList(testedData));
    }

    public static long find(long toElement) {
        return tree.higher(toElement);
    }


    public static void main(String[] args) {
        initData();

        long startTime = System.nanoTime();

        for (long i = 0; i < 100000000L; i++) {
            find(1478323448L);
        }

        long totalTime = System.nanoTime() - startTime;
        System.out.println("total time : " + totalTime + " " + (totalTime * 1.0 / 100000000L));
    }
}
