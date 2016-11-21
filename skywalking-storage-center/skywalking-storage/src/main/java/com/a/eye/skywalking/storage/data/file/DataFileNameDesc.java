package com.a.eye.skywalking.storage.data.file;

import com.a.eye.datacarrier.common.AtomicRangeInteger;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by xin on 2016/11/16.
 */
public class DataFileNameDesc {
    private static final AtomicRangeInteger DATA_FILE_NAME_SUFFIX = new AtomicRangeInteger(1000, 9999);
    private long   name;
    private int    suffix;
    private String fileNameStr;

    public DataFileNameDesc() {
        name = System.currentTimeMillis();
        suffix = DATA_FILE_NAME_SUFFIX.getAndIncrement();
        fileNameStr = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS").format(name) + "_" + suffix;
    }

    public DataFileNameDesc(long name, int suffix) {
        this.name = name;
        this.suffix = suffix;
        fileNameStr = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS").format(name) + "_" + suffix;
    }

    public DataFileNameDesc(String fileName) {
        int lastIndex = fileName.lastIndexOf('_');
        try {
            this.name = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS").parse(fileName.substring(0, lastIndex - 1))
                    .getTime();
        } catch (ParseException e) {
        }
        this.suffix = Integer.parseInt(fileName.substring(lastIndex + 1));
        fileNameStr = fileName;
    }


    public String fileName() {
        return fileNameStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        DataFileNameDesc that = (DataFileNameDesc) o;

        if (name != that.name)
            return false;
        return suffix == that.suffix;

    }

    @Override
    public int hashCode() {
        int result = (int) (name ^ (name >>> 32));
        result = 31 * result + suffix;
        return result;
    }

    public long getName() {
        return name;
    }

    public int getSuffix() {
        return suffix;
    }
}
