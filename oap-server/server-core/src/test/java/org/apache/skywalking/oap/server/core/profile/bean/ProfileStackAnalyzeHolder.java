package org.apache.skywalking.oap.server.core.profile.bean;

import lombok.Data;

import java.util.List;

@Data
public class ProfileStackAnalyzeHolder {

    private List<ProfileStackAnalyze> list;

}
