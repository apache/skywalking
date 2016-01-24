package com.ai.cloud.skywalking.analysis.categorize2chain;

import java.io.IOException;
import java.util.Iterator;

import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;

public class Categorize2ChainReducer extends Reducer<Text, ChainInfo, Text, IntWritable> {
    private static Logger logger = LoggerFactory.getLogger(Categorize2ChainReducer.class.getName());

    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException, InterruptedException {
        ConfigInitializer.initialize();
        int totalCount = reduceAction(key.toString(), values.iterator());
        context.write(new Text(key.toString()), new IntWritable(totalCount));
    }

    public static int reduceAction(String key, Iterator<ChainInfo> chainInfoIterator) throws IOException, InterruptedException {
        int totalCount = 0;
        try {
            ChainRelationship chainRelate = HBaseUtil.selectCallChainRelationship(key.toString());
            ChainSummary summary = new ChainSummary();
            while (chainInfoIterator.hasNext()) {
                ChainInfo chainInfo = chainInfoIterator.next();
                try {
                    chainRelate.categoryChain(chainInfo);
                    summary.summary(chainInfo);
                } catch (Exception e) {
                    continue;
                }
                totalCount++;
            }

            chainRelate.save();
            summary.save();
        } catch (Exception e) {
            logger.error("Failed to reduce key[" + key + "]", e);
        }

        return totalCount;
    }
}
