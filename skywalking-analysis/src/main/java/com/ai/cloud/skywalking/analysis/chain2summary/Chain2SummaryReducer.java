package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.chain2summary.po.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

public class Chain2SummaryReducer extends Reducer<Text, ChainSpecificTimeSummary, Text, IntWritable> {
    private Logger logger = LoggerFactory
            .getLogger(Chain2SummaryReducer.class);

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        ConfigInitializer.initialize();
    }

    @Override
    protected void reduce(Text key, Iterable<ChainSpecificTimeSummary> values, Context context) throws IOException, InterruptedException {
        doReduceAction(Bytes.toString(key.getBytes()), values.iterator());
    }

    public void doReduceAction(String key, Iterator<ChainSpecificTimeSummary> summaryIterator) {
        try {
            ChainRelationship4Search chainRelationship = ChainRelationship4Search.load(Bytes.toString(key.getBytes()));
            Summary summary = new Summary();
            while (summaryIterator.hasNext()) {
                try {
                    ChainSpecificTimeSummary timeSummary = summaryIterator.next();
                    summary.summary(timeSummary, chainRelationship);
                } catch (Exception e) {
                    logger.error("Failed to reduce", e);
                }
            }

            summary.saveToHBase();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to reduce key=" + Bytes.toString(key.getBytes()), e);
        }
    }
}
