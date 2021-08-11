package org.apache.skywalking.banyandb.client.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class BanyanDBQueryResponse {
    @Setter
    private int total;

    @Getter
    private final List<BanyanDBEntity> entities;

    public BanyanDBQueryResponse() {
        entities = new ArrayList<>();
    }
}
