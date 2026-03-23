package com.alibaba.arthas.idea.workbench.model;

import java.util.Objects;

/**
 * 统一描述 Arthas 包来源类型及其附带值。
 */
public final class PackageSourceSpec {

    private final PackageSourceType type;
    private final String value;

    public PackageSourceSpec(PackageSourceType type) {
        this(type, "");
    }

    public PackageSourceSpec(PackageSourceType type, String value) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = value == null ? "" : value;
    }

    public PackageSourceType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
