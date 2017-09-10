package org.skywalking.apm.collector.storage.h2;

import java.util.List;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.storage.StorageModuleDefine;
import org.skywalking.apm.collector.storage.StorageModuleGroupDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.dao.H2DAODefineLoader;
import org.skywalking.apm.collector.storage.h2.define.H2StorageInstaller;

/**
 * @author pengys5
 */
public class StorageH2ModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "h2";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageH2ConfigParser();
    }

    @Override protected Client createClient() {
        return new H2Client();
    }

    @Override public StorageInstaller storageInstaller() {
        return new H2StorageInstaller();
    }

    @Override public void injectClientIntoDAO(Client client) throws DefineException {
        H2DAODefineLoader loader = new H2DAODefineLoader();
        List<H2DAO> h2DAOs = loader.load();
        h2DAOs.forEach(h2DAO -> {
            h2DAO.setClient((H2Client)client);
            String interFaceName = h2DAO.getClass().getInterfaces()[0].getName();
            DAOContainer.INSTANCE.put(interFaceName, h2DAO);
        });
    }
}
