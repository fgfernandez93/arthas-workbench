package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * Arthas 包来源枚举。
 */
public enum PackageSourceType {
    OFFICIAL_LATEST("enum.package.source.official_latest.name", "enum.package.source.official_latest.hint"),
    OFFICIAL_VERSION("enum.package.source.official_version.name", "enum.package.source.official_version.hint"),
    CUSTOM_REMOTE_ZIP("enum.package.source.custom_remote_zip.name", "enum.package.source.custom_remote_zip.hint"),
    LOCAL_ZIP("enum.package.source.local_zip.name", "enum.package.source.local_zip.hint"),
    LOCAL_PATH("enum.package.source.local_path.name", "enum.package.source.local_path.hint");

    private final String displayNameKey;
    private final String hintKey;

    PackageSourceType(String displayNameKey, String hintKey) {
        this.displayNameKey = displayNameKey;
        this.hintKey = hintKey;
    }

    public String getDisplayName() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }

    public String getHint() {
        return ArthasWorkbenchBundle.message(hintKey);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
