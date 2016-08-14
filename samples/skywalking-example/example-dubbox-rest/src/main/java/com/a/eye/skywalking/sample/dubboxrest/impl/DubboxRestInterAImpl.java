package com.a.eye.skywalking.sample.dubboxrest.impl;

import com.a.eye.skywalking.sample.dubboxrest.interfaces.IDubboxRestInterA;
import com.a.eye.skywalking.sample.dubboxrest.interfaces.param.DubboxRestInterAParameter;
import com.alibaba.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

@Service
public class DubboxRestInterAImpl implements IDubboxRestInterA {

    @Autowired
    private DataSource dataSource;

    public String doBusiness(DubboxRestInterAParameter paramA) {
        try {
            Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement =
                    connection.prepareStatement("INSERT INTO PUBLIC.sampletable1(key1,value1) VALUES(?,?)");
            preparedStatement.setString(1, UUID.randomUUID().toString());
            preparedStatement.setString(2, paramA.getParameterA());
            int updateCount = preparedStatement.executeUpdate();
            return "{\"updateCount\":\"" + updateCount + "\"}";
        } catch (SQLException e) {
            return "{\"Message\":\"Update failed\"}";
        }
    }
}
