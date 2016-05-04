package com.ai.cloud.skywalking.analysis.chainbuild;

import com.ai.cloud.skywalking.analysis.chainbuild.action.IStatisticsAction;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SummaryType;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
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
        Config.AnalysisServer.IS_ACCUMULATE_MODE = Boolean.parseBoolean(context
                .getConfiguration().get("skywalking.analysis.mode", "false"));
        logger.info("Skywalking analysis mode :[{}]",
                Config.AnalysisServer.IS_ACCUMULATE_MODE ? "ACCUMULATE"
                        : "REWRITE");
    }

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        String reduceKey = key.toString();

        int index = reduceKey.indexOf(":");
        if (index == -1) {
            return;
        }
        String summaryTypeAndDateStr = reduceKey.substring(0, index);
        String entryKey = reduceKey.substring(index + 1);

        IStatisticsAction summaryAction = SummaryType.chooseSummaryAction(
                summaryTypeAndDateStr, entryKey);
        doReduceAction(entryKey, summaryAction, values.iterator());
    }

    public void doReduceAction(String reduceKey, IStatisticsAction summaryAction,
                               Iterator<Text> iterator) {
        long dataCounter = 0;
        while (iterator.hasNext()) {
            String summaryData = iterator.next().toString();
            try {
                summaryAction.doAction(summaryData);
            } catch (Exception e) {
                logger.error(
                        "Failed to summary call chain, maybe illegal data:"
                                + summaryData, e);
            } finally {
                dataCounter++;
            }
            if (dataCounter % 1000 == 0) {
                logger.debug("reduce for key: {}, count: {}", reduceKey, dataCounter);
            }
        }

        try {
            summaryAction.doSave();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to save summaryresult/chainTree.", e);
        }
    }
}
