package com.ai.cloud.skywalking.analysis.mapper;


import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildMapper;
import com.ai.cloud.skywalking.analysis.chainbuild.ChainBuildReducer;
import com.ai.cloud.skywalking.analysis.chainbuild.action.IStatisticsAction;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainInfo;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.po.SummaryType;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.analysis.config.HBaseTableMetaData;
import com.ai.cloud.skywalking.protocol.Span;
import com.google.gson.Gson;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by astraea on 2016/1/15.
 */
public class CallChainMapperTest {

    private static String ZK_QUORUM = "10.1.235.197,10.1.235.198,10.1.235.199";
    //    private static String ZK_QUORUM = "10.1.241.18,10.1.241.19,10.1.241.20";
    private static String ZK_CLIENT_PORT = "29181";
    private static String chain_Id = "1.0b.1461569643178.5b468e7.23292.118.68";
    //private static String chain_Id = "1.0a2.1453429608422.2701d43.6468.56.1";

    private static Configuration configuration = null;
    private static Connection connection;


    private static SimpleDateFormat hourSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH");
    private static SimpleDateFormat daySimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat monthSimpleDateFormat = new SimpleDateFormat("yyyy-MM");
    private static SimpleDateFormat yearSimpleDateFormat = new SimpleDateFormat("yyyy");

    public static void main(String[] args) throws Exception {
        ConfigInitializer.initialize();
        initHBaseClient();
        // 2016-04-22/23:57:03 to 2016-05-02/23:47:03
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");
        Date startDate = simpleDateFormat.parse("2016-04-22/23:57:03");
        Date endDate = simpleDateFormat.parse("2016-05-02/23:47:03");
        Scan scan = new Scan();
        scan.setTimeRange(startDate.getTime(), endDate.getTime());
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        ResultScanner result = table.getScanner(scan);
        int count = 0;
        for (Result result1  : result){
            count++;
        }
        System.out.println(count);

//        List<Span> spanList = selectByTraceId(chain_Id);
//        ChainInfo chainInfo = ChainBuildMapper.spanToChainInfo(chain_Id, spanList);
//        List<Text> chainNodeInfo = new ArrayList<>();
//        for (ChainNode chainNode : chainInfo.getNodes()) {
//            List<Text> value1 = new ArrayList<Text>();
//            Text key =new Text(SummaryType.YEAR.getValue() + "-" + yearSimpleDateFormat.format(
//                    new Date(chainNode.getStartDate())
//            ) + ":" + chainInfo.getCallEntrance());
//            value1.add(new Text(new Gson().toJson(chainNode)));
//
//            String reduceKey = Bytes.toString(key.getBytes());
//            int index = reduceKey.indexOf(":");
//            if (index == -1) {
//                return;
//            }
//
//            String summaryTypeAndDateStr = reduceKey.substring(0, index);
//            String entryKey = reduceKey.substring(index + 1);
//            IStatisticsAction summaryAction = SummaryType.chooseSummaryAction(summaryTypeAndDateStr, entryKey);
//
//            new ChainBuildReducer().doReduceAction(reduceKey, summaryAction, value1.iterator());
//        }
    }

    public static List<Span> selectByTraceId(String traceId) throws IOException {
        List<Span> entries = new ArrayList<Span>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        Result r = table.get(g);
        for (Cell cell : r.rawCells()) {
            if (cell.getValueArray().length > 0)
                entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
        }
        return entries;
    }

    @Test
    public void validateSummaryResult() throws IOException, ParseException {
        List<ChainInfo> chainInfoList = selectSpecificCallEntranceSpansOnSpecificCallTime("com.ai.aisse.core.service.impl.SynchAisseWorkDataServiceImpl.SynchAisseDataDel()", "2016-01-26/10:00:00", "2016-02-08/10:00:00");

        System.out.println("size :" + chainInfoList.size());
        Map<String, Integer> summaryResult = new HashMap<String, Integer>();
        for (ChainInfo chainInfo : chainInfoList) {
            String key = generateKey(chainInfo.getStartDate());
            Integer total = summaryResult.get(key);
            if (total == null) {
                total = 0;
            }
            summaryResult.put(key, ++total);
        }

        for (Map.Entry<String, Integer> entry : summaryResult.entrySet()) {
            System.out.println(entry.getKey() + "  " + entry.getValue());
        }

    }

    private String generateKey(long startDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(startDate));
        return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static List<ChainInfo> selectSpecificCallEntranceSpansOnSpecificCallTime(String callEntrance, String startDate, String endDate) throws IOException, ParseException {
        List<ChainInfo> chainInfos = new ArrayList<ChainInfo>();
        Table table = connection.getTable(TableName.valueOf(HBaseTableMetaData.TABLE_CALL_CHAIN.TABLE_NAME));
        Scan scan = new Scan();
        scan.setTimeRange(new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss").parse(startDate).getTime(), new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss").parse(endDate).getTime());
        ResultScanner resultScanner = table.getScanner(scan);
        Iterator<Result> resultIterator = resultScanner.iterator();
        while (resultIterator.hasNext()) {
            Result result = resultIterator.next();
            List<Span> entries = new ArrayList<Span>();
            for (Cell cell : result.rawCells()) {
                if (cell.getValueArray().length > 0)
                    entries.add(new Span(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength())));
            }
            ChainInfo chainInfo = null;
            try {
                chainInfo = ChainBuildMapper.spanToChainInfo(Bytes.toString(result.getRow()), entries);
            } catch (Exception e) {
                continue;
            }
            // System.out.println(chainInfo.getCallEntrance());
            if (callEntrance.equalsIgnoreCase(chainInfo.getCallEntrance())) {
                chainInfos.add(chainInfo);
            }
        }

        return chainInfos;
    }

    public static void initHBaseClient() throws IOException {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            configuration.set("hbase.zookeeper.quorum", ZK_QUORUM);
            configuration.set("hbase.zookeeper.property.clientPort", ZK_CLIENT_PORT);
            configuration.set("hbase.rpc.timeout", "600000");
            connection = ConnectionFactory.createConnection(configuration);
        }

        ConfigInitializer.initialize();
    }
}