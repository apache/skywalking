package com.a.eye.skywalking.web.service.inter;

import com.a.eye.skywalking.web.entity.BreviaryChainTree;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 16-4-6.
 */
public interface ICallChainTreeService {
    List<BreviaryChainTree> queryCallChainTreeByKey(String uid, String viewpoint, int pageSize) throws SQLException, IOException;
}
