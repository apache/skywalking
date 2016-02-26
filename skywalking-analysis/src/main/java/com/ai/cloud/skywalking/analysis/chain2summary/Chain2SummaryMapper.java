package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.chain2summary.po.ChainSpecificTimeSummary;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Chain2SummaryMapper extends TableMapper<Text, ChainSpecificTimeSummary> {

    private Logger logger = LoggerFactory
            .getLogger(Chain2SummaryMapper.class);


    @Override
    protected void setup(Context context) throws IOException,
            InterruptedException {
        ConfigInitializer.initialize();
    }

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException, InterruptedException {
        try {
            ChainSpecificTimeSummary summary = new ChainSpecificTimeSummary(Bytes.toString(key.get()));
            for (Cell cell : value.rawCells()) {
                summary.addChainNodeSummaryResult(Bytes.toString(cell.getValueArray(),
                        cell.getValueOffset(), cell.getValueLength()));
            }
            context.write(new Text(summary.buildMapperKey()), summary);
        } catch (Exception e) {
            logger.error("Failed to mapper call chain[" + key.toString() + "]",
                    e);
        }
    }
}
