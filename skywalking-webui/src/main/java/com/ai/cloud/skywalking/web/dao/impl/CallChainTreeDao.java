package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.ICallChainTreeDao;
import com.ai.cloud.skywalking.web.entity.CallChainTree;
import com.ai.cloud.skywalking.web.util.HBaseUtils;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;

@Repository
public class CallChainTreeDao implements ICallChainTreeDao {

    @Autowired
    private HBaseUtils hBaseUtils;

    @Override
    public CallChainTree queryTreeId(String treeId) throws IOException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf("sw-chain-1day-summary"));
        Get get = new Get(treeId.getBytes());
        Result result = table.get(get);
        if (result.rawCells().length == 0) {
            return null;
        }
        CallChainTree chainTree = new CallChainTree(treeId);
        for (Cell cell : result.rawCells()) {
            if (cell.getValueArray().length > 0) {
                String qualifier = Bytes.toString(cell.getQualifierArray(),
                        cell.getQualifierOffset(), cell.getQualifierLength());
                String[] qualifierArr = qualifier.split("@");
                chainTree.addNode(qualifierArr[0],qualifierArr[1]);
            }

        }
        return null;
    }
}
