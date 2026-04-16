package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import java.util.Set;
import org.junit.Test;

/**
 * {@link ArthasSessionService} 的会话状态管理测试。
 */
public class ArthasSessionServiceTest {

    @Test
    /**
     * 验证同一 PID 的最新会话能够覆盖旧会话，并正确记录统一会话窗口状态。
     */
    public void shouldTrackOpenStateAndLatestSessionPerPid() {
        ArthasSessionService service = new ArthasSessionService();
        service.addOrUpdateSession(session("session-1", 1001L, SessionStatus.ATTACHING));
        service.markLogsOpen("session-1", true);

        ArthasSessionService.SessionSnapshot firstSnapshot = service.findSnapshot("session-1");
        assertNotNull(firstSnapshot);
        assertFalse(firstSnapshot.isTerminalOpen());
        assertTrue(firstSnapshot.isLogsOpen());
        assertTrue(firstSnapshot.isSessionWindowOpen());
        assertEquals(ArthasSessionViewType.LOG, firstSnapshot.getSelectedViewType());

        service.addOrUpdateSession(session("session-2", 1001L, SessionStatus.RUNNING));
        service.openTerminalAndLogs("session-2");

        ArthasSessionService.SessionSnapshot latest = service.findLatestByPid(1001L);
        assertNotNull(latest);
        assertEquals("session-2", latest.getId());
        assertTrue(latest.isTerminalOpen());
        assertFalse(latest.isLogsOpen());
        assertTrue(latest.isSessionWindowOpen());
        assertEquals(ArthasSessionViewType.TERMINAL, latest.getSelectedViewType());
        assertEquals(SessionStatus.RUNNING, latest.getSession().getStatus());
    }

    @Test
    /**
     * 验证进程退出或进程列表缺失时，会话会被自动标记为停止。
     */
    public void shouldMarkStoppedByPidAndMissingProcesses() {
        ArthasSessionService service = new ArthasSessionService();
        service.addOrUpdateSession(session("session-1", 2001L, SessionStatus.RUNNING));
        service.addOrUpdateSession(session("session-2", 2002L, SessionStatus.ATTACHING));

        assertTrue(service.markStoppedByPid(2001L));
        assertEquals(
                SessionStatus.STOPPED,
                service.findSnapshot("session-1").getSession().getStatus());

        assertTrue(service.markStoppedByMissingProcesses(Set.of(2001L)));
        assertEquals(
                SessionStatus.STOPPED,
                service.findSnapshot("session-2").getSession().getStatus());
    }

    /**
     * 构造测试用会话对象。
     */
    private ArthasSession session(String id, long pid, SessionStatus status) {
        return new ArthasSession(
                id,
                pid,
                "demo.Process-" + pid,
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
    }
}
