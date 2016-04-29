package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.ITypicalCallTreeDao;
import com.ai.cloud.skywalking.web.dto.TypicalCallTree;
import com.ai.cloud.skywalking.web.dto.TypicalCallTreeNode;
import com.ai.cloud.skywalking.web.util.HBaseUtils;
import com.alibaba.fastjson.JSONArray;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;

/**
 * Created by xin on 16-4-28.
 */
@Repository
public class TypicalCallTreeDaoImpl implements ITypicalCallTreeDao {

    @Autowired
    private HBaseUtils hBaseUtils;

    @Override
    public String[] queryAllCombineCallChainTreeIds(String rowKey) throws IOException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf("sw-treeId-cid-mapping"));
        Get g = new Get(Bytes.toBytes(rowKey));

        Result r = table.get(g);
        if (r.rawCells().length == 0) {
            return null;
        }
        Cell cell = r.getColumnLatestCell("cids".getBytes(), "been_merged_cid".getBytes());
        JSONArray result = JSONArray.parseArray(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
        return result.toArray(new String[result.size()]);
    }

    @Override
    public TypicalCallTree queryCallChainTree(String callTreeId) throws IOException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf("sw-chain-detail"));
        Get g = new Get(Bytes.toBytes(callTreeId));

        Result r = table.get(g);
        if (r.rawCells().length == 0) {
            return null;
        }
        TypicalCallTree callTree = new TypicalCallTree(callTreeId);
        for (Cell cell : r.rawCells()) {
            String levelId = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
            String viewPoint = Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
            callTree.addNode(new TypicalCallTreeNode(levelId, viewPoint));
        }
        return callTree;
    }
}
