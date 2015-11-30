package com.ai.cloud.skywalking.example.order.dao.mapper.interfaces;

import com.ai.cloud.skywalking.example.order.dao.mapper.bo.TOrder;
import com.ai.cloud.skywalking.example.order.dao.mapper.bo.TOrderCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TOrderMapper {
    int countByExample(TOrderCriteria example);

    int deleteByExample(TOrderCriteria example);

    int deleteByPrimaryKey(String orderId);

    int insert(TOrder record);

    int insertSelective(TOrder record);

    List<TOrder> selectByExample(TOrderCriteria example);

    TOrder selectByPrimaryKey(String orderId);

    int updateByExampleSelective(@Param("record") TOrder record, @Param("example") TOrderCriteria example);

    int updateByExample(@Param("record") TOrder record, @Param("example") TOrderCriteria example);

    int updateByPrimaryKeySelective(TOrder record);

    int updateByPrimaryKey(TOrder record);
}