package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 保存项目级 Arthas 会话状态、日志内容以及会话窗口打开情况。
 */
@Service(Service.Level.PROJECT)
public final class ArthasSessionService {

    private final Map<String, SessionRecord> records = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long titleSequence = 0;
    private long revisionSequence = 0;

    /**
     * 注册或更新会话；首次注册时会分配稳定的页签标题。
     */
    public synchronized String addOrUpdateSession(ArthasSession session) {
        SessionRecord existing = records.get(session.getId());
        if (existing == null) {
            titleSequence += 1;
            existing = new SessionRecord(
                    session.getId(),
                    ArthasWorkbenchBundle.message("session.snapshot.title", session.getPid(), titleSequence),
                    session);
            records.put(session.getId(), existing);
        } else {
            existing.session = session;
        }
        existing.revision = nextRevision();
        fireChanged();
        return existing.title;
    }

    public synchronized void appendLog(String sessionId, String line) {
        SessionRecord record = records.get(sessionId);
        if (record == null) {
            return;
        }
        record.logBuilder.append(line).append('\n');
        fireChanged();
    }

    public synchronized void removeSession(String sessionId) {
        if (records.remove(sessionId) != null) {
            fireChanged();
        }
    }

    public synchronized List<SessionSnapshot> snapshots() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (SessionRecord record : records.values()) {
            snapshots.add(snapshotOf(record));
        }
        return snapshots;
    }

    public synchronized SessionSnapshot findSnapshot(String sessionId) {
        SessionRecord record = records.get(sessionId);
        return record == null ? null : snapshotOf(record);
    }

    public synchronized SessionSnapshot findLatestByPid(long pid) {
        SessionRecord candidate = null;
        for (SessionRecord record : records.values()) {
            if (record.session.getPid() != pid) {
                continue;
            }
            if (candidate == null || record.revision > candidate.revision) {
                candidate = record;
            }
        }
        return candidate == null ? null : snapshotOf(candidate);
    }

    public synchronized void markConsoleOpen(String sessionId, boolean open) {
        openSessionWindow(sessionId, open ? ArthasSessionViewType.CONSOLE : null);
    }

    public synchronized void markLogsOpen(String sessionId, boolean open) {
        openSessionWindow(sessionId, open ? ArthasSessionViewType.LOG : null);
    }

    public synchronized void openConsoleAndLogs(String sessionId) {
        openSessionWindow(sessionId, ArthasSessionViewType.CONSOLE);
    }

    public synchronized void openSessionWindow(String sessionId, ArthasSessionViewType viewType) {
        SessionRecord record = records.get(sessionId);
        if (record == null) {
            return;
        }
        if (viewType == null) {
            if (!record.sessionWindowOpen) {
                return;
            }
            record.sessionWindowOpen = false;
            fireChanged();
            return;
        }
        boolean changed = !record.sessionWindowOpen || record.selectedViewType != viewType;
        record.sessionWindowOpen = true;
        record.selectedViewType = viewType;
        if (changed) {
            fireChanged();
        }
    }

    public synchronized void closeSessionWindow(String sessionId) {
        openSessionWindow(sessionId, null);
    }

    public synchronized void setSelectedViewType(String sessionId, ArthasSessionViewType viewType) {
        SessionRecord record = records.get(sessionId);
        if (record == null || viewType == null) {
            return;
        }
        boolean changed = record.selectedViewType != viewType;
        if (!record.sessionWindowOpen) {
            record.sessionWindowOpen = true;
            changed = true;
        }
        record.selectedViewType = viewType;
        if (changed) {
            fireChanged();
        }
    }

    public synchronized boolean markStoppedByPid(long pid) {
        boolean changed = false;
        for (SessionRecord record : records.values()) {
            if (record.session.getPid() != pid) {
                continue;
            }
            SessionStatus status = record.session.getStatus();
            if (status == SessionStatus.RUNNING || status == SessionStatus.ATTACHING) {
                record.session = record.session.withStatus(SessionStatus.STOPPED);
                record.revision = nextRevision();
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
        return changed;
    }

    public synchronized boolean markStoppedByMissingProcesses(Set<Long> activePids) {
        boolean changed = false;
        for (SessionRecord record : records.values()) {
            SessionStatus status = record.session.getStatus();
            if ((status == SessionStatus.RUNNING || status == SessionStatus.ATTACHING)
                    && !activePids.contains(record.session.getPid())) {
                record.session = record.session.withStatus(SessionStatus.STOPPED);
                record.revision = nextRevision();
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
        return changed;
    }

    public void addListener(Runnable listener, Disposable parentDisposable) {
        listeners.add(listener);
        Disposer.register(parentDisposable, () -> listeners.remove(listener));
    }

    private void fireChanged() {
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            for (Runnable listener : listeners) {
                listener.run();
            }
            return;
        }
        application.invokeLater(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    private SessionSnapshot snapshotOf(SessionRecord record) {
        return new SessionSnapshot(
                record.id,
                record.title,
                record.session,
                record.logBuilder.toString(),
                record.sessionWindowOpen,
                record.selectedViewType);
    }

    private long nextRevision() {
        revisionSequence += 1;
        return revisionSequence;
    }

    private static final class SessionRecord {
        private final String id;
        private final String title;
        private final StringBuilder logBuilder = new StringBuilder();
        private ArthasSession session;
        private boolean sessionWindowOpen;
        private ArthasSessionViewType selectedViewType = ArthasSessionViewType.LOG;
        private long revision;

        private SessionRecord(String id, String title, ArthasSession session) {
            this.id = id;
            this.title = title;
            this.session = session;
        }
    }

    /**
     * 提供给 UI 层消费的只读会话快照。
     */
    public static final class SessionSnapshot {
        private final String id;
        private final String title;
        private final ArthasSession session;
        private final String logs;
        private final boolean sessionWindowOpen;
        private final ArthasSessionViewType selectedViewType;

        public SessionSnapshot(
                String id,
                String title,
                ArthasSession session,
                String logs,
                boolean sessionWindowOpen,
                ArthasSessionViewType selectedViewType) {
            this.id = id;
            this.title = title;
            this.session = session;
            this.logs = logs;
            this.sessionWindowOpen = sessionWindowOpen;
            this.selectedViewType = selectedViewType == null ? ArthasSessionViewType.LOG : selectedViewType;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public ArthasSession getSession() {
            return session;
        }

        public String getLogs() {
            return logs;
        }

        public boolean isSessionWindowOpen() {
            return sessionWindowOpen;
        }

        public ArthasSessionViewType getSelectedViewType() {
            return selectedViewType;
        }

        public boolean isConsoleOpen() {
            return sessionWindowOpen && selectedViewType == ArthasSessionViewType.CONSOLE;
        }

        public boolean isLogsOpen() {
            return sessionWindowOpen && selectedViewType == ArthasSessionViewType.LOG;
        }
    }
}
