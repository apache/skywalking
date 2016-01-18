package com.ai.cloud.skywalking.analysis;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.mapper.CallChainMapper;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.mapreduce.Job;
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
        int res = ToolRunner.run(new AnalysisServerDriver(), args);
        System.exit(res);
    }

    @Override
    public int run(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 2) {
            System.err.println("Usage: AnalysisServer yyyy-MM-dd/HH:mm:ss yyyy-MM-dd/HH:mm:ss");
            System.exit(2);
        }
        Job job = Job.getInstance(conf);
        job.setJarByClass(AnalysisServerDriver.class);
        Scan scan = buildHBaseScan(args);

        TableMapReduceUtil.initTableMapperJob(Config.HBase.CALL_CHAIN_TABLE_NAME, scan, CallChainMapper.class,
                String.class, ChainInfo.class, job);
        //TableMapReduceUtil.initTableReducerJob("sw-call-chain-model", CallChainReducer.class, job);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    private Scan buildHBaseScan(String[] args) throws ParseException, IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");
        Date startDate = simpleDateFormat.parse(args[0]);
        Date endDate = simpleDateFormat.parse(args[1]);
        Scan scan = new Scan();
        scan.setTimeRange(startDate.getTime(), endDate.getTime());
        return scan;
    }
}
