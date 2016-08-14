package com.a.eye.skywalking.analysis.chainbuild.action;


import java.io.IOException;
import java.sql.SQLException;

public interface IStatisticsAction {
    void doAction(String summaryData) throws IOException;

    void doSave() throws InterruptedException, SQLException, IOException;
}
