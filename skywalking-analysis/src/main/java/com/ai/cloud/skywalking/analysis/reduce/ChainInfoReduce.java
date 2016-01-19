package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.io.Text;

import java.io.IOException;

public class ChainInfoReduce extends TableReducer<Text, ChainInfo, Put> {
    @Override
    protected void reduce(Text key, Iterable<ChainInfo> values, Context context) throws IOException, InterruptedException {
        String[] keyArray = key.toString().split(":");
        String userId = keyArray[0];
        String firstNode = keyArray[1];

        //

    }
}
