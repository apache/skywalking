package org.apache.skywalking.oap.server.core.query.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProfileStackTree {

    private List<ProfileStackElement> elements;

}
