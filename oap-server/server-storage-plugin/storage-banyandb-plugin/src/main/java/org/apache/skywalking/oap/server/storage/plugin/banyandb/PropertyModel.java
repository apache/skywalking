package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property;

@RequiredArgsConstructor
@Getter
public class PropertyModel {
    private final Property property;
}
