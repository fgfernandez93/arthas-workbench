package com.alibaba.arthas.idea.workbench.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.service.ArthasSessionService;
import org.junit.Test;

/**
 * 会话 Tool Window 页签关闭规则测试。
 */
public class ArthasToolWindowPanelStateTest {

    @Test
    /**
     * 验证只有停止或失败的会话允许手动关闭页签。
     */
    public void shouldAllowManualCloseOnlyAfterSessionStopsOrFails() {
        assertFalse(ArthasSessionsToolWindowPanel.isManualCloseAllowed(snapshot(SessionStatus.ATTACHING)));
        assertFalse(ArthasSessionsToolWindowPanel.isManualCloseAllowed(snapshot(SessionStatus.RUNNING)));
        assertTrue(ArthasSessionsToolWindowPanel.isManualCloseAllowed(snapshot(SessionStatus.STOPPED)));
        assertTrue(ArthasSessionsToolWindowPanel.isManualCloseAllowed(snapshot(SessionStatus.FAILED)));
    }

    /**
     * 构造测试用的会话快照。
     */
    private ArthasSessionService.SessionSnapshot snapshot(SessionStatus status) {
        ArthasSession session = new ArthasSession(
                "session-1",
                1001L,
                "demo-app",
                8563,
                3658,
                "/mcp",
                "secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                "/tmp/arthas",
                status);
        return new ArthasSessionService.SessionSnapshot(
                "session-1", "PID 1001 #1", session, "", true, ArthasSessionViewType.LOG);
    }
}
