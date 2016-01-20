package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import com.ai.cloud.skywalking.analysis.model.ChainRelate;
import com.ai.cloud.skywalking.analysis.model.Summary;
import com.ai.cloud.skywalking.analysis.util.HBaseUtil;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.io.Text;

import java.io.IOException;
import java.util.Iterator;

public class ChainInfoReduce extends TableReducer<Text, ChainInfo, Put> {
    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException, InterruptedException {

        ChainRelate chainRelate = HBaseUtil.selectCallChainRelationship(key.toString());
        Summary summary = new Summary();
        Iterator<ChainInfo> chainInfoIterator = values.iterator();
        while (chainInfoIterator.hasNext()) {
            ChainInfo chainInfo = chainInfoIterator.next();
            chainRelate.addRelate(chainInfo);
            summary.summary(chainInfo);
        }

        chainRelate.save();
        // 入HBase库（关系表，Info表，汇总表）
        summary.save();
        // 入Mysql表

    }
}
