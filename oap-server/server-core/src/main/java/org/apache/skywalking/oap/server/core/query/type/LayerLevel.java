package org.apache.skywalking.oap.server.core.query.type;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LayerLevel {
    private String layer;
    private int level;
}
