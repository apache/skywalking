package com.a.eye.skywalking.storage;

import com.a.eye.skywalking.storage.data.file.DataFileNameDesc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * Created by xin on 2016/11/30.
 */
public class TestFile {
    public static void main(String[] args) throws IOException {
//        File file = new File("/Users/xin/workbench/data/file", "2016_11_29_23_02_55_517_1000");
//        System.out.println(file.length());
//
//        FileInputStream byteInputStream = new FileInputStream(file);
//        byte[] bytes = new byte[1024];
//        int count = 0;
//        int length = 0;
//        while ((count = byteInputStream.read(bytes)) != -1) {
//            length += count;
//        }
//
//        System.out.println(length);

        long startTime = System.currentTimeMillis();
        System.out.println(startTime);
        System.out.println(new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS").format(startTime));
    }
}
