package com.alibaba.arthas.idea.workbench;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

/**
 * 插件统一的国际化入口。
 * 所有需要展示给用户的文本都应优先从这里读取，避免在界面代码里散落硬编码字符串。
 */
public final class ArthasWorkbenchBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.MyBundle";

    private static final ArthasWorkbenchBundle INSTANCE = new ArthasWorkbenchBundle();

    private ArthasWorkbenchBundle() {
        super(BUNDLE);
    }

    /**
     * 读取指定 key 对应的国际化文案。
     *
     * @param key    资源文件中的消息 key
     * @param params 占位符参数
     * @return 当前语言环境下的格式化文本
     */
    public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
