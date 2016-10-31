package com.a.eye.skywalking.storage.index;

import com.a.eye.skywalking.storage.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TimeRangeOfIndexDataFile {
    private static       Logger                   logger    = LogManager.getLogger(TimeRangeOfIndexDataFile.class);
    private final        String                   FILE_NAME = "time_range.index";
    private static final TimeRangeOfIndexDataFile INSTANCE  = new TimeRangeOfIndexDataFile();
    private final File indexFile;

    private TimeRangeOfIndexDataFile() {
        indexFile = new File(Config.Index_DB.TIME_RANGE_INDEX_FILE_PATH, FILE_NAME);
        if (!indexFile.exists()) {
            indexFile.getParentFile().mkdirs();

            try {
                indexFile.createNewFile();
            } catch (IOException e) {
                logger.error("Failed to create time_range.index", e);
                System.exit(-1);
            }
        }
    }

    public void write(TimeRangeOfIndexData timeRange) {
        try {
            FileWriter writer = new FileWriter(indexFile);
            writer.write(
                    timeRange.getIndexDataFileName() + "\t" + timeRange.getStartTime() + "\t" + timeRange.getEndTime());
        } catch (IOException e) {
            logger.error("Failed to write {} to index file", timeRange, e);
        }
    }

    public List<TimeRangeOfIndexData> read() {
        List<TimeRangeOfIndexData> indexData = new ArrayList<TimeRangeOfIndexData>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(indexFile));
            String indexDataStr = null;
            while ((reader.readLine()) != null) {
                String[] indexSegment = indexDataStr.split("\t");
                indexData.add(new TimeRangeOfIndexData(indexSegment[0], Long.parseLong(indexSegment[1]),
                        Long.parseLong(indexSegment[2])));
            }
        } catch (IOException e) {
            logger.error("Failed to read data from index file", e);
        }

        return indexData;
    }


    public static TimeRangeOfIndexDataFile INSTANCE() {
        return INSTANCE;
    }
}
