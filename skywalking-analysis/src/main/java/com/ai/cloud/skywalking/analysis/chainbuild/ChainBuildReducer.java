package com.ai.cloud.skywalking.analysis.chainbuild;

import com.ai.cloud.skywalking.analysis.chainbuild.entity.CallChainTree;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SpecificTimeCallTreeMergedChainIdContainer;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SummaryType;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Iterator;

public class ChainBuildReducer extends Reducer<Text, Text, Text, IntWritable> {
    private Logger logger = LogManager.getLogger(ChainBuildReducer.class);

    @Override
    protected void setup(Context context) throws IOException,
            InterruptedException {
        ConfigInitializer.initialize();
        Config.AnalysisServer.IS_ACCUMULATE_MODE = Boolean.parseBoolean(context.getConfiguration()
                .get("skywalking.analysis.mode", "false"));
        logger.info("Skywalking analysis mode :[{}]",
                Config.AnalysisServer.IS_ACCUMULATE_MODE ? "ACCUMULATE" : "REWRITE");
    }

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        String reduceKey = Bytes.toString(key.getBytes());
        int index = reduceKey.indexOf("@#!");
        if (index == -1){
            return;
        }

        doReduceAction(reduceKey.substring(0, index - 1), SummaryType.convert(reduceKey.substring(index + 3)), values.iterator());
    }

    public void doReduceAction(String key, SummaryType summaryType, Iterator<Text> chainInfoIterator)
            throws IOException, InterruptedException {
        CallChainTree chainTree = CallChainTree.load(key);
        SpecificTimeCallTreeMergedChainIdContainer container
                = new SpecificTimeCallTreeMergedChainIdContainer(chainTree.getTreeToken());
        while (chainInfoIterator.hasNext()) {
            String callChainData = chainInfoIterator.next().toString();
            ChainInfo chainInfo = null;
            try {
                chainInfo = new Gson().fromJson(callChainData, ChainInfo.class);
                container.addMergedChainIfNotContain(chainInfo);
                chainTree.summary(chainInfo, summaryType);
            } catch (Exception e) {
                logger.error(
                        "Failed to summary call chain, maybe illegal data:"
                                + callChainData, e);
            }
        }
        try {
            container.saveToHBase(summaryType);
            chainTree.saveToHbase();
        } catch (Exception e) {
            logger.error("Failed to save summaryresult/chainTree.", e);
        }
    }
}
