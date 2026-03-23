package com.alibaba.arthas.idea.workbench.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link UiToolkit} 的文本压缩能力测试。
 */
public class UiToolkitTest {

    @Test
    /**
     * 验证多行和超长文本会被归一化为单行摘要。
     */
    public void shouldCompactMultiLineTextIntoSingleLineSummary() {
        String raw =
                "demo.Main\n\t--classpath    /Users/weil/Desktop/workspaces/opensource/arthas/some/really/long/path";

        String compact = UiToolkit.compactSingleLine(raw, 48);

        assertTrue(compact.length() <= 48);
        assertTrue(compact.contains("..."));
        assertEquals(-1, compact.indexOf('\n'));
        assertEquals(-1, compact.indexOf('\t'));
    }
}
