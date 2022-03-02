package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.junit.Assert;
import org.junit.Test;

public class MySQLTableInstallerTest {
    @Test
    public void test() throws Exception {
        new DefaultScopeDefine.Listener().notify(Event.class);
        Model m = new StorageModels().add(Event.class, DefaultScopeDefine.EVENT,
            new Storage(null, false, null), false);

        m.getColumns().stream()
            .filter(c -> Event.PARAMETERS.equals(c.getColumnName().getName()))
            .findAny().ifPresent(c -> {
                String sql = new TestTableInstaller().getColumn(c);
//                System.out.println(sql);
                Assert.assertTrue("Should contains 'TEXT'",
                    sql.contains("TEXT"));
            });
    }

    private static class TestTableInstaller extends MySQLTableInstaller {
        public TestTableInstaller() {
            super(null, null, -1, -1);
        }

        @Override
        protected void overrideColumnName(String columnName, String newName) {
        }
    }
}
