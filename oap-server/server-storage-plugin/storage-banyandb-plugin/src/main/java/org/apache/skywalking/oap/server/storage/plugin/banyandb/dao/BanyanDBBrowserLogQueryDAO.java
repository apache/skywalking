package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;

import java.io.IOException;

public class BanyanDBBrowserLogQueryDAO implements IBrowserLogQueryDAO {
    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId, BrowserErrorCategory category, long startSecondTB, long endSecondTB, int limit, int from) throws IOException {
        return new BrowserErrorLogs();
    }
}
