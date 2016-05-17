package com.ai.cloud.skywalking.analysis;

import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildReducer;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AnalysisServerDriver extends Configured implements Tool {

    private static Logger logger = LoggerFactory.getLogger(AnalysisServerDriver.class.getName());

    public static void main(String[] args) throws Exception {
        logger.info("Begin to analysis call chain.");

        String analysisMode = System.getenv("skywalking.analysis.mode");

        if ("rewrite".equalsIgnoreCase(analysisMode)) {
            logger.info("Skywalking analysis mode will switch to [REWRITE] mode");
            Config.AnalysisServer.IS_ACCUMULATE_MODE = false;
        } else {
            logger.info("Skywalking analysis mode will switch to [ACCUMULATE] mode");
            Config.AnalysisServer.IS_ACCUMULATE_MODE = true;
        }

        int res = ToolRunner.run(new AnalysisServerDriver(), args);

        System.exit(res);
    }

    @Override
    public int run(String[] args) throws Exception {
        ConfigInitializer.initialize();
        Configuration conf = new Configuration();
        conf.set("skywalking.analysis.mode", String.valueOf(Config.AnalysisServer.IS_ACCUMULATE_MODE));
        conf.set("hbase.zookeeper.quorum", Config.HBase.ZK_QUORUM);
        conf.set("hbase.zookeeper.property.clientPort", Config.HBase.ZK_CLIENT_PORT);
        //-XX:+UseParallelGC -XX:ParallelGCThreads=4 -XX:GCTimeRatio=10 -XX:YoungGenerationSizeIncrement=20 -XX:TenuredGenerationSizeIncrement=20 -XX:AdaptiveSizeDecrementScaleFactor=2
        conf.set("mapred.child.java.opts", Config.MapReduce.JAVA_OPTS);
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 2) {
            System.err.println("Usage: com.ai.cloud.skywalking.analysis.AnalysisServerDriver yyyy-MM-dd/HH:mm:ss yyyy-MM-dd/HH:mm:ss");
            System.exit(2);
        }

        Job job = Job.getInstance(conf);
        job.setJarByClass(AnalysisServerDriver.class);

        Scan scan = buildHBaseScan(args);

        TableMapReduceUtil.initTableMapperJob(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME, scan, ChainBuildMapper.class,
                Text.class, Text.class, job);

        job.setReducerClass(ChainBuildReducer.class);
        job.setNumReduceTasks(Config.Reducer.REDUCER_NUMBER);
        job.setOutputFormatClass(NullOutputFormat.class);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    private Scan buildHBaseScan(String[] args) throws ParseException, IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");
        Date startDate = simpleDateFormat.parse(args[0]);
        Date endDate = simpleDateFormat.parse(args[1]);
        Scan scan = new Scan();
        scan.setBatch(2001);
        scan.setTimeRange(startDate.getTime(), endDate.getTime());
        return scan;
    }
}
