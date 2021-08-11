package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.io.IOException;

public class BanyanDBHistoryDeleteDAO implements IHistoryDeleteDAO {
    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
    }
}
