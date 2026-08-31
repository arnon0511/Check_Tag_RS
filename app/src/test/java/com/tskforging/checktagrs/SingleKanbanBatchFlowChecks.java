package com.tskforging.checktagrs;

public final class SingleKanbanBatchFlowChecks {
    private static final String A = "TG028993-590A";
    private static final String B = "TG028993-590B";
    private static int count;
    private static void check(boolean result) { if (!result) throw new AssertionError("Check failed"); }
    private static void scenario(String name, Runnable test) {
        try { test.run(); count++; System.out.println("PASS " + name); }
        catch (Throwable t) { throw new AssertionError(name, t); }
    }
    private static SingleKanbanBatchFlow ready() {
        SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, String::equals);
        check(f.accept(A)); check(f.accept(A)); return f;
    }
    public static int runAll() {
        count = 0;
        scenario("Stand then one Kanban immediately enters Box", () -> {
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, String::equals);
            check(f.getTarget() == SingleKanbanBatchFlow.Target.STAND); check(!f.finish());
            check(f.accept(A)); check(f.getTarget() == SingleKanbanBatchFlow.Target.KANBAN);
            check(f.accept(A)); check(f.getTarget() == SingleKanbanBatchFlow.Target.BOX_TAG);
            check(A.equals(f.getKanbanPart())); check(!f.finish());
        });
        scenario("one Kanban checks many Boxes without requesting more Kanbans", () -> {
            SingleKanbanBatchFlow f = ready();
            for (int i = 1; i <= 5; i++) {
                check(f.accept(A)); check(f.getBoxes().size() == i);
                check(f.getTarget() == SingleKanbanBatchFlow.Target.BOX_TAG);
                check(A.equals(f.getKanbanPart())); check(!f.getComplete());
            }
            check(f.finish());
        });
        scenario("one Box can finish only with explicit BOX complete", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A));
            check(f.canFinish()); check(!f.getComplete()); check(f.finish());
        });
        scenario("wrong Kanban does not set reference or advance", () -> {
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, String::equals);
            check(f.accept(A)); check(!f.accept(B)); check(f.getKanbanPart() == null);
            check(f.getTarget() == SingleKanbanBatchFlow.Target.KANBAN);
            check(f.accept(A)); check(!f.getScanError());
        });
        scenario("wrong Box does not count and blocks finish until corrected", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A)); check(!f.accept(B));
            check(f.getBoxes().size() == 1); check(!f.finish());
            check(f.accept(A)); check(f.getBoxes().size() == 2); check(f.finish());
        });
        scenario("invalid parser result leaves reference and count unchanged", () -> {
            SingleKanbanBatchFlow f = ready(); check(!f.accept(null)); check(!f.accept(""));
            check(A.equals(f.getKanbanPart())); check(f.getBoxes().isEmpty()); check(!f.finish());
            check(f.accept(A)); check(f.finish());
        });
        scenario("skip Stand still compares every Box against Kanban", () -> {
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(false, String::equals);
            check(f.getTarget() == SingleKanbanBatchFlow.Target.KANBAN);
            check(f.accept(A)); check(!f.accept(B)); check(f.accept(A)); check(f.finish());
        });
        scenario("clear last Box retains Stand and Kanban", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A)); check(f.accept(A));
            check(f.getLastRemovableTarget() == SingleKanbanBatchFlow.Target.BOX_TAG);
            check(f.removeLast()); check(f.getBoxes().size() == 1);
            check(A.equals(f.getStandPart())); check(A.equals(f.getKanbanPart())); check(f.finish());
        });
        scenario("clear only Box requires another Box before finishing", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A)); check(f.removeLast());
            check(f.getBoxes().isEmpty()); check(!f.finish());
            check(f.accept(A)); check(f.getBoxes().get(0).getScanNo() == 1); check(f.finish());
        });
        scenario("clear Kanban before Boxes goes back to Kanban", () -> {
            SingleKanbanBatchFlow f = ready();
            check(f.getLastRemovableTarget() == SingleKanbanBatchFlow.Target.KANBAN);
            check(A.equals(f.getLastRemovablePart())); check(f.removeLast());
            check(f.getKanbanPart() == null); check(A.equals(f.getStandPart()));
            check(f.getTarget() == SingleKanbanBatchFlow.Target.KANBAN); check(!f.finish());
            check(f.accept(A)); check(f.accept(A)); check(f.finish());
        });
        scenario("clear Stand before Kanban goes back to Stand", () -> {
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, String::equals);
            check(!f.removeLast()); check(f.accept(A));
            check(f.getLastRemovableTarget() == SingleKanbanBatchFlow.Target.STAND);
            check(f.removeLast()); check(f.getStandPart() == null);
            check(f.getTarget() == SingleKanbanBatchFlow.Target.STAND);
        });
        scenario("clear last item never dismisses a rejected scan", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A)); check(!f.accept(B));
            check(f.removeLast()); check(f.getScanError()); check(!f.finish());
            check(f.accept(A)); check(f.finish());
        });
        scenario("completed batch is immutable and has nothing removable", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A)); check(f.finish());
            check(f.getLastRemovableTarget() == null); check(!f.removeLast());
            check(!f.accept(A)); check(!f.finish()); check(f.getBoxes().size() == 1);
        });
        scenario("new batch clears previous references counts and error", () -> {
            SingleKanbanBatchFlow old = ready(); check(old.accept(A)); check(!old.accept(B));
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, String::equals);
            check(f.getStandPart() == null); check(f.getKanbanPart() == null); check(f.getBoxes().isEmpty());
            check(!f.getScanError()); check(!f.getComplete()); check(f.getLastRemovableTarget() == null);
        });
        scenario("customer callback is used for all comparisons", () -> {
            SingleKanbanBatchFlow f = new SingleKanbanBatchFlow(true, (a,b) -> a.substring(0,9).equals(b.substring(0,9)));
            check(f.accept("JGC123456-40")); check(f.accept("JGC123456-99"));
            check(f.accept("JGC123456-31-2")); check(!f.accept("JGC999999-31-2"));
            check(f.accept("JGC123456-40")); check(f.finish());
        });
        scenario("Box list cannot be externally cleared", () -> {
            SingleKanbanBatchFlow f = ready(); check(f.accept(A));
            try { f.getBoxes().clear(); throw new AssertionError("Mutable list"); }
            catch (UnsupportedOperationException expected) { }
            check(f.finish());
        });
        System.out.println("Passed " + count + " production-flow scenarios");
        return count;
    }
}
