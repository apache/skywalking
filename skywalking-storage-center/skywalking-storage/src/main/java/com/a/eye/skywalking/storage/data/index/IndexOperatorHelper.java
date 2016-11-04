package com.a.eye.skywalking.storage.data.index;

import java.sql.Connection;

public class IndexOperatorHelper {

    public IndexOperatorHelper(Connection connection) {
        
    }

    public boolean validateIsReady(String tableName) {
        return false;
    }

    public void maintain() {
        createTable();
        createIndex();
    }

    private void createIndex() {

    }

    private void createTable() {

    }
}
