package org.apache.skywalking.oap.server.core.query.type;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsyncProfilerStackElement {
    // work for tree building, id matches multiple parentId
    private int id;
    private int parentId;

    // stack code signature
    private String codeSignature;

    private long total;
    private long self;
}
