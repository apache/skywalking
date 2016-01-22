package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.config.Constants;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

public class ChainInfoReduce extends TableReducer<Text, ChainInfo, Put> {
    private static Logger logger = LoggerFactory.getLogger(ChainInfoReduce.class.getName());

    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException, InterruptedException {
        reduceAction(key.toString(), values.iterator());
    }

    public static void reduceAction(String key, Iterator<ChainInfo> chainInfoIterator) throws IOException, InterruptedException {
        if (Constants.EXCEPTIONMAPPER.equals(key)) {
            logger.info("Skip Exception Mapper.....");
            return;
        }

        try {
            ChainRelate chainRelate = HBaseUtil.selectCallChainRelationship(key.toString());
            Summary summary = new Summary();
            while (chainInfoIterator.hasNext()) {
                ChainInfo chainInfo = chainInfoIterator.next();
                try {
                    chainRelate.addRelate(chainInfo);
                    summary.summary(chainInfo);
                } catch (Exception e) {
                    continue;
                }
            }

            chainRelate.save();
            summary.save();
        } catch (Exception e) {
            logger.error("Failed to reduce key[" + key + "]", e);
        }
    }
}
