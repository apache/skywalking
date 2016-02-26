package com.ai.cloud.skywalking.analysis.categorize2chain;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainRelationship;
import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainSummaryWithoutRelationship;
import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.categorize2chain.util.HBaseUtil;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;

public class Categorize2ChainReducer extends Reducer<Text, ChainInfo, Text, IntWritable> {
    private static Logger logger = LoggerFactory.getLogger(Categorize2ChainReducer.class.getName());

    @Override
	protected void setup(Context context) throws IOException,
			InterruptedException {
		ConfigInitializer.initialize();
	}
    
    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException, InterruptedException {
        int totalCount = reduceAction(key.toString(), values.iterator());
        context.write(new Text(key.toString()), new IntWritable(totalCount));
    }

    public static int reduceAction(String key, Iterator<ChainInfo> chainInfoIterator) throws IOException, InterruptedException {
        int totalCount = 0;
        try {
            ChainRelationship chainRelate = HBaseUtil.loadCallChainRelationship(key.toString());
            ChainSummaryWithoutRelationship summary = new ChainSummaryWithoutRelationship();
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
