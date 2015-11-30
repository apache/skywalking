package com.ai.cloud.skywalking.example.account.dao.mapper.interfaces;

import com.ai.cloud.skywalking.example.account.dao.mapper.bo.TAccount;
import com.ai.cloud.skywalking.example.account.dao.mapper.bo.TAccountCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TAccountMapper {
    int countByExample(TAccountCriteria example);

    int deleteByExample(TAccountCriteria example);

    int deleteByPrimaryKey(int accountId);

    int insert(TAccount record);

    int insertSelective(TAccount record);

    List<TAccount> selectByExample(TAccountCriteria example);

    TAccount selectByPrimaryKey(int accountId);

    int updateByExampleSelective(@Param("record") TAccount record, @Param("example") TAccountCriteria example);

    int updateByExample(@Param("record") TAccount record, @Param("example") TAccountCriteria example);

    int updateByPrimaryKeySelective(TAccount record);

    int updateByPrimaryKey(TAccount record);
}