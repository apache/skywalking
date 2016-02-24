package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.Iterator;

public class Chain2SummaryReducer extends Reducer<Text, ChainSpecificTimeSummary, Text, IntWritable> {

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        ConfigInitializer.initialize();
    }

    @Override
    protected void reduce(Text key, Iterable<ChainSpecificTimeSummary> values, Context context) throws IOException, InterruptedException {
        ChainRelationship4Search chainRelationship = ChainRelationship4Search.load(Bytes.toString(key.getBytes()));
        Iterator<ChainSpecificTimeSummary> summaryIterator = values.iterator();
        Summary summary = new Summary();
        while (summaryIterator.hasNext()) {
            ChainSpecificTimeSummary timeSummary = summaryIterator.next();
            summary.summary(timeSummary, chainRelationship);
        }

        summary.saveToHBase();
    }
}
