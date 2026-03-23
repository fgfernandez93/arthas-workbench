package com.alibaba.arthas.idea.workbench.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 表示已经解析完成、可直接用于 attach 的本地 Arthas 包。
 */
public final class ResolvedArthasPackage {

    private final PackageSourceSpec source;
    private final String label;
    private final Path bootJar;
    private final Path arthasHome;

    public ResolvedArthasPackage(PackageSourceSpec source, String label, Path bootJar, Path arthasHome) {
        this.source = Objects.requireNonNull(source, "source");
        this.label = Objects.requireNonNull(label, "label");
        this.bootJar = Objects.requireNonNull(bootJar, "bootJar");
        this.arthasHome = arthasHome;
    }

    public PackageSourceSpec getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }

    public Path getBootJar() {
        return bootJar;
    }

    public Path getArthasHome() {
        return arthasHome;
    }
}
