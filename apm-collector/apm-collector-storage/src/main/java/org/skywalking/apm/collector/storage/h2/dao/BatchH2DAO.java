package org.skywalking.apm.collector.storage.h2.dao;

import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.dao.IBatchDAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class BatchH2DAO extends H2DAO implements IBatchDAO {
    private final Logger logger = LoggerFactory.getLogger(BatchH2DAO.class);

    @Override
    public void batchPersistence(List<?> batchCollection) {
        if (batchCollection != null && batchCollection.size() > 0) {
            logger.debug("the batch collection size is {}", batchCollection.size());
            Connection conn = null;
            final Map<String, PreparedStatement> batchSqls = new HashMap<>();
            try {
                conn = getClient().getConnection();
                conn.setAutoCommit(false);
                PreparedStatement ps;
                for (Object entity : batchCollection) {
                    H2SqlEntity e = getH2SqlEntity(entity);
                    String sql = e.getSql();
                    if (batchSqls.containsKey(sql)) {
                        ps = batchSqls.get(sql);
                    } else {
                        ps = conn.prepareStatement(sql);
                        batchSqls.put(sql, ps);
                    }

                    Object[] params = e.getParams();
                    if (params != null) {
                        logger.debug("the sql is {}, params size is {}", e.getSql(), params.length);
                        for (int i = 0; i < params.length; i++) {
                            ps.setObject(i + 1, params[i]);
                        }
                    }
                    ps.addBatch();
                }

                for (String k : batchSqls.keySet()) {
                    batchSqls.get(k).executeBatch();
                }
                conn.commit();
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    logger.error(e.getMessage(), e1);
                }
            }
            batchSqls.clear();
        }
    }

    private H2SqlEntity getH2SqlEntity(Object entity) {
        if (entity instanceof H2SqlEntity) {
            return (H2SqlEntity) entity;
        }
        return null;
    }
}
