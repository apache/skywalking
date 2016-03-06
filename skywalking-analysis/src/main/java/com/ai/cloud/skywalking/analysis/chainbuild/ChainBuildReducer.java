package com.ai.cloud.skywalking.analysis.chainbuild;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

public class ChainBuildReducer extends Reducer<Text, ChainInfo, Text, IntWritable> {

    private Logger logger = LoggerFactory
            .getLogger(ChainBuildReducer.class.getName());

    @Override
    protected void setup(Context context) throws IOException,
            InterruptedException {
        ConfigInitializer.initialize();
    }

    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException,
            InterruptedException {
        CallChainTree chainTree = CallChainTree.load(Bytes.toString(key.getBytes()));
        Iterator<ChainInfo> chainInfoIterator = values.iterator();
        while (chainInfoIterator.hasNext()) {
            ChainInfo chainInfo = chainInfoIterator.next();
            if (chainInfo.getChainStatus() == ChainInfo.ChainStatus.NORMAL) {
                chainTree.processMerge(chainInfo);
            }
            //合并数据
            chainTree.summary(chainInfo);
        }

        chainTree.saveToHbase();
    }
}
