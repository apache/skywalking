package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.dao.inter.ICallChainTreeDao;
import com.ai.cloud.skywalking.web.entity.CallChainTree;
import com.ai.cloud.skywalking.web.util.HBaseUtils;
import com.google.gson.Gson;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;

@Repository
public class CallChainTreeDao implements ICallChainTreeDao {

    private Logger logger = LogManager.getLogger(CallChainTree.class);

    @Autowired
    private HBaseUtils hBaseUtils;

    @Override
    public AnlyResult queryEntranceAnlyResult(String entranceColumnName, String treeId) throws IOException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf("sw-chain-1day-summary"));
        Get get = new Get(treeId.getBytes());
        Result result = table.get(get);
        if (result.rawCells().length == 0) {
            return null;
        }
        Cell cell = result.getColumnLatestCell("chain_summary".getBytes(), entranceColumnName.getBytes());
        if (cell != null) {
            String anlyResultStr = Bytes.toString(cell.getQualifierArray(),
                    cell.getQualifierOffset(), cell.getQualifierLength());
            logger.debug("traceId: {} , entranceColumnName : {}, anlyResultStr : {}",
                    treeId, entranceColumnName, anlyResultStr);
            return new Gson().fromJson(anlyResultStr, AnlyResult.class);
        }

        return new AnlyResult();
    }
}
