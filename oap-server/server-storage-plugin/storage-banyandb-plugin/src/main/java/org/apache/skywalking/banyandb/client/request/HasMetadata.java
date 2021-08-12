package org.apache.skywalking.banyandb.client.request;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@EqualsAndHashCode
public class HasMetadata {
    private final String group;
    private final String name;
}
