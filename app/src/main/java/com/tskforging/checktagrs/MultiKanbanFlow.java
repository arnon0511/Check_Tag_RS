package com.tskforging.checktagrs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/** Collect every Kanban first; each accepted Box consumes exactly one matching Kanban. */
public final class MultiKanbanFlow {
    public enum Target { STAND, KANBAN, BOX_TAG }

    public static final class Kanban {
        private final int scanNo;
        private final String partNo;
        private Integer boxScanNo;
        private Kanban(int scanNo, String partNo) { this.scanNo = scanNo; this.partNo = partNo; }
        public int getScanNo() { return scanNo; }
        public String getPartNo() { return partNo; }
        public Integer getBoxScanNo() { return boxScanNo; }
    }

    public static final class Box {
        private final int scanNo;
        private final String partNo;
        private final Kanban kanban;
        private Box(int scanNo, String partNo, Kanban kanban) {
            this.scanNo = scanNo; this.partNo = partNo; this.kanban = kanban;
        }
        public int getScanNo() { return scanNo; }
        public String getPartNo() { return partNo; }
        public int getKanbanScanNo() { return kanban.scanNo; }
        public String getKanbanPart() { return kanban.partNo; }
    }

    private final boolean checkStand;
    private final BiPredicate<String, String> partsMatch;
    private final List<Kanban> kanbans = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private Target target;
    private String standPart;
    private boolean scanError;
    private boolean complete;

    public MultiKanbanFlow(boolean checkStand, BiPredicate<String, String> partsMatch) {
        this.checkStand = checkStand;
        this.partsMatch = partsMatch;
        target = checkStand ? Target.STAND : Target.KANBAN;
    }

    public Target getTarget() { return target; }
    public String getStandPart() { return standPart; }
    public List<Kanban> getKanbans() { return Collections.unmodifiableList(kanbans); }
    public List<Box> getBoxes() { return Collections.unmodifiableList(boxes); }
    public boolean getScanError() { return scanError; }
    public boolean getComplete() { return complete; }
    public int getRemaining() { return kanbans.size() - boxes.size(); }

    public String getReferencePart() {
        if (target == Target.KANBAN) return standPart;
        for (Kanban kanban : kanbans) if (kanban.boxScanNo == null) return kanban.partNo;
        return null;
    }

    private Kanban firstUnmatched(String part) {
        for (Kanban kanban : kanbans)
            if (kanban.boxScanNo == null && partsMatch.test(kanban.partNo, part)) return kanban;
        return null;
    }

    public String comparison(String part) {
        if (complete) return "COMPLETE";
        if (part == null || part.isEmpty()) return "NOT_COMPARED";
        if (target == Target.STAND) return "REFERENCE";
        if (checkStand && (standPart == null || !partsMatch.test(standPart, part))) return "MISMATCH";
        if (target == Target.KANBAN) return checkStand ? "MATCH" : "REFERENCE";
        return firstUnmatched(part) != null ? "MATCH" : "NO_UNMATCHED_KANBAN";
    }

    public boolean accept(String part) {
        if (complete) return false;
        String result = comparison(part);
        if (!result.equals("REFERENCE") && !result.equals("MATCH")) {
            scanError = true;
            return false;
        }
        scanError = false;
        if (target == Target.STAND) {
            standPart = part;
            target = Target.KANBAN;
        } else if (target == Target.KANBAN) {
            kanbans.add(new Kanban(kanbans.size() + 1, part));
            // Stay here until the operator explicitly presses KANBAN complete.
        } else {
            Kanban kanban = firstUnmatched(part);
            Box box = new Box(boxes.size() + 1, part, kanban);
            kanban.boxScanNo = box.scanNo;
            boxes.add(box);
        }
        return true;
    }

    public boolean finishKanbans() {
        if (complete || scanError || target != Target.KANBAN || kanbans.isEmpty()) return false;
        target = Target.BOX_TAG;
        return true;
    }

    public boolean canFinish() {
        return !complete && !scanError && target == Target.BOX_TAG && !boxes.isEmpty() && getRemaining() == 0;
    }

    public boolean finish() {
        if (!canFinish()) return false;
        complete = true;
        return true;
    }

    /** Editing counts never dismisses an outstanding rejected scan. */
    public boolean removeLast() {
        if (complete) return false;
        if (target == Target.KANBAN && !kanbans.isEmpty()) {
            kanbans.remove(kanbans.size() - 1);
            return true;
        }
        if (target == Target.BOX_TAG && !boxes.isEmpty()) {
            Box removed = boxes.remove(boxes.size() - 1);
            removed.kanban.boxScanNo = null;
            return true;
        }
        return false;
    }
}
