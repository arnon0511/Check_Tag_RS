package com.tskforging.checktagrs;

/** Runs the actual production state machine with no Android or JUnit dependency. */
public final class MultiKanbanFlowChecks {
    private static final String A = "TG028993-590A";
    private static final String B = "TG028993-590B";
    private static int count;
    private static void check(boolean result) { if (!result) throw new AssertionError("Check failed"); }
    private static void scenario(String name, Runnable test) {
        try { test.run(); count++; System.out.println("PASS " + name); }
        catch (Throwable t) { throw new AssertionError(name, t); }
    }
    private static MultiKanbanFlow ready(int n) {
        MultiKanbanFlow f = new MultiKanbanFlow(true, String::equals);
        check(f.accept(A));
        for (int i = 0; i < n; i++) check(f.accept(A));
        check(f.finishKanbans());
        return f;
    }
    public static int runAll() {
        count = 0;
        scenario("no empty batch or early phase completion", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(true, String::equals);
            check(f.getTarget() == MultiKanbanFlow.Target.STAND);
            check(!f.finishKanbans()); check(!f.finish());
            check(f.accept(A)); check(!f.finishKanbans()); check(!f.finish());
        });
        scenario("three Kanbans first then three Boxes; explicit completion", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(true, String::equals);
            check(f.accept(A));
            for (int i = 1; i <= 3; i++) {
                check(f.accept(A)); check(f.getTarget() == MultiKanbanFlow.Target.KANBAN);
                check(f.getKanbans().size() == i); check(f.getBoxes().isEmpty());
            }
            check(f.finishKanbans()); check(!f.finish());
            for (int i = 1; i <= 3; i++) {
                check(f.accept(A)); check(f.getBoxes().size() == i);
                check(f.getBoxes().get(i - 1).getKanbanScanNo() == i);
                check(f.getRemaining() == 3 - i); check(!f.getComplete());
            }
            check(f.finish());
        });
        scenario("wrong Kanban is not counted and blocks phase change", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(true, String::equals);
            check(f.accept(A)); check(f.accept(A)); check(!f.accept(B));
            check(f.getKanbans().size() == 1); check(!f.finishKanbans());
            check(f.accept(A)); check(!f.getScanError()); check(f.finishKanbans());
        });
        scenario("invalid parser result blocks advance and does not count", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(false, String::equals);
            check(!f.accept(null)); check(!f.accept("")); check(f.getKanbans().isEmpty());
            check(!f.finishKanbans()); check(f.accept(A)); check(f.finishKanbans());
        });
        scenario("unmatched Box does not consume a Kanban", () -> {
            MultiKanbanFlow f = ready(2);
            check(!f.accept(B)); check(f.getRemaining() == 2); check(!f.finish());
            check(f.accept(A)); check(f.getRemaining() == 1); check(!f.finish());
            check(f.accept(A)); check(f.finish());
        });
        scenario("one Kanban cannot approve multiple Boxes", () -> {
            MultiKanbanFlow f = ready(1);
            check(f.accept(A)); check(!f.accept(A));
            check(f.getBoxes().size() == 1); check(f.getRemaining() == 0); check(!f.finish());
        });
        scenario("missing Boxes prevent completion", () -> {
            MultiKanbanFlow f = ready(3);
            check(f.accept(A)); check(f.accept(A));
            check(f.getRemaining() == 1); check(!f.canFinish()); check(!f.finish());
        });
        scenario("skip Stand matches mixed parts in any Box order", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(false, String::equals);
            check(f.getTarget() == MultiKanbanFlow.Target.KANBAN);
            check(f.accept(A)); check(f.accept(B)); check(f.accept(A)); check(f.finishKanbans());
            check(f.accept(B)); check(f.getBoxes().get(0).getKanbanScanNo() == 2);
            check(f.accept(A)); check(f.getBoxes().get(1).getKanbanScanNo() == 1);
            check(f.accept(A)); check(f.getBoxes().get(2).getKanbanScanNo() == 3);
            check(f.finish());
        });
        scenario("remove last Kanban before locking count", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(false, String::equals);
            check(!f.removeLast()); check(f.accept(A)); check(f.accept(B));
            check(f.removeLast()); check(f.getKanbans().size() == 1);
            check(f.finishKanbans()); check(f.accept(A)); check(f.finish());
        });
        scenario("removing only Kanban cannot finish phase", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(false, String::equals);
            check(f.accept(A)); check(f.removeLast()); check(!f.finishKanbans());
        });
        scenario("removing Box releases its exact Kanban", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(false, String::equals);
            check(f.accept(A)); check(f.accept(B)); check(f.finishKanbans());
            check(f.accept(B)); check(f.accept(A)); check(f.removeLast());
            check(f.getKanbans().get(0).getBoxScanNo() == null);
            check(f.getKanbans().get(1).getBoxScanNo() == 1);
            check(f.getRemaining() == 1); check(!f.finish());
            check(f.accept(A)); check(f.getBoxes().get(1).getKanbanScanNo() == 1); check(f.finish());
        });
        scenario("clear button does not dismiss rejected scan", () -> {
            MultiKanbanFlow f = ready(2); check(f.accept(A)); check(!f.accept(B));
            check(f.removeLast()); check(f.getScanError()); check(!f.finish());
            check(f.accept(A)); check(f.accept(A)); check(f.finish());
        });
        scenario("recover from excess scan by clear and rescan", () -> {
            MultiKanbanFlow f = ready(1); check(f.accept(A)); check(!f.accept(A));
            check(f.removeLast()); check(f.getScanError()); check(f.accept(A)); check(f.finish());
        });
        scenario("completed batch is immutable", () -> {
            MultiKanbanFlow f = ready(1); check(f.accept(A)); check(f.finish());
            check(!f.accept(A)); check(!f.accept(null)); check(!f.removeLast());
            check(!f.finishKanbans()); check(!f.finish()); check(f.getBoxes().size() == 1);
        });
        scenario("new batch carries no previous references", () -> {
            MultiKanbanFlow old = ready(1); check(old.accept(A)); check(old.finish());
            MultiKanbanFlow f = new MultiKanbanFlow(true, String::equals);
            check(f.getStandPart() == null); check(f.getKanbans().isEmpty()); check(f.getBoxes().isEmpty());
        });
        scenario("customer comparison callback is applied to Stand Kanban and Box", () -> {
            MultiKanbanFlow f = new MultiKanbanFlow(true, (a,b) -> a.substring(0,9).equals(b.substring(0,9)));
            check(f.accept("JGC123456-40")); check(f.accept("JGC123456-99"));
            check(f.finishKanbans()); check(!f.accept("JGC999999-99"));
            check(f.accept("JGC123456-31-2")); check(f.finish());
        });
        scenario("same part multiple labels are separate pairs", () -> {
            MultiKanbanFlow f = ready(2); check(f.accept(A)); check(f.accept(A));
            check(f.getKanbans().get(0).getBoxScanNo() == 1);
            check(f.getKanbans().get(1).getBoxScanNo() == 2); check(f.finish());
        });
        scenario("collection getters cannot edit counts", () -> {
            MultiKanbanFlow f = ready(1);
            try { f.getKanbans().clear(); throw new AssertionError("Mutable Kanbans"); }
            catch (UnsupportedOperationException expected) { }
            check(f.accept(A));
            try { f.getBoxes().clear(); throw new AssertionError("Mutable Boxes"); }
            catch (UnsupportedOperationException expected) { }
            check(f.finish());
        });
        System.out.println("Passed " + count + " production-flow scenarios");
        return count;
    }
    public static void main(String[] args) { runAll(); }
}
