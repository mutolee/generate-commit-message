package org.aicommitmessage;

/** 管理应用级检查节流、并发请求和通知去重。 */
final class UpdateCheckPolicy {
    private State state = new State();
    private boolean running;
    private String notifiedVersion = "";
    /** 可持久化的检查记录，不包含项目引用。 */
    public static final class State {
        public long lastAutomaticCheck;
        public String ignoredVersion = "";
    }

    /** 尝试取得检查资格；手动检查绕过时间限制。 */
    synchronized boolean begin(boolean manual, long now) {
        if (running) return false; // 自动与手动请求共享同一把锁。
        long elapsed = now - state.lastAutomaticCheck;
        if (!manual && state.lastAutomaticCheck > 0 && elapsed >= 0 && elapsed < 86_400_000L) {
            return false; // 采用滚动 24 小时窗口，跨午夜也不密集请求。
        }
        running = true;
        if (!manual) state.lastAutomaticCheck = now; // 请求失败同样计入节流。
        return true;
    }
    /** 释放检查资格，包括失败的请求。 */
    synchronized void finish() { running = false; }
    /** 判断是否应提示指定版本，并记录本次提示。 */
    synchronized boolean claimNotification(String version, boolean manual) {
        if (!manual && (version.equals(state.ignoredVersion) || version.equals(notifiedVersion))) {
            return false; // 自动检查尊重忽略记录，并在本次 IDE 会话中去重。
        }
        notifiedVersion = version;
        return true;
    }
    /** 保存用户忽略的版本。 */
    synchronized void ignore(String version) { state.ignoredVersion = version; }
    /** 返回独立的持久化快照。 */
    synchronized State snapshot() {
        State copy = new State();
        copy.lastAutomaticCheck = state.lastAutomaticCheck;
        copy.ignoredVersion = state.ignoredVersion;
        return copy;
    }
    /** 恢复持久化记录。 */
    synchronized void restore(State saved) {
        state = new State();
        state.lastAutomaticCheck = saved.lastAutomaticCheck;
        state.ignoredVersion = saved.ignoredVersion;
    }
}
