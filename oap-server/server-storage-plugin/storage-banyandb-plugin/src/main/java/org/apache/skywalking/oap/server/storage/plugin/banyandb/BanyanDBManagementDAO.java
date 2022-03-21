package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.io.IOException;

@RequiredArgsConstructor
public class BanyanDBManagementDAO implements IManagementDAO {
    private final BanyanDBStorageClient client;
    private final StorageBuilder<ManagementData> storageBuilder;

    @Override
    public void insert(Model model, ManagementData storageData) throws IOException {

    }
}
