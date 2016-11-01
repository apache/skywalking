package com.a.eye.skywalking.storage.index;

public interface DataFileIndexFinder {
    DataIndexMetaData find(long timestamp);
}
