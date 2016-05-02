package com.ai.cloud.skywalking.analysis.chainbuild.action;


import java.io.IOException;
import java.sql.SQLException;

public interface ISummaryAction {
    void doAction(String summaryData) throws IOException;

    void doSave() throws InterruptedException, SQLException, IOException;
}
