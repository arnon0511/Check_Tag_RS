package com.tskforging.checktagrs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/** Employee is handled by the Activity; one accepted Kanban checks every Box until BOX complete. */
public final class SingleKanbanBatchFlow {
    public enum Target { STAND, KANBAN, BOX_TAG }

    public static final class Box {
        private final int scanNo;
        private final String partNo;
        private Box(int scanNo, String partNo) { this.scanNo = scanNo; this.partNo = partNo; }
        public int getScanNo() { return scanNo; }
        public String getPartNo() { return partNo; }
    }

    private final boolean checkStand;
    private final BiPredicate<String, String> partsMatch;
    private final List<Box> boxes = new ArrayList<>();
    private Target target;
    private String standPart;
    private String kanbanPart;
    private boolean scanError;
    private boolean complete;

    public SingleKanbanBatchFlow(boolean checkStand, BiPredicate<String, String> partsMatch) {
        this.checkStand = checkStand;
        this.partsMatch = partsMatch;
        target = checkStand ? Target.STAND : Target.KANBAN;
    }

    public Target getTarget() { return target; }
    public String getStandPart() { return standPart; }
    public String getKanbanPart() { return kanbanPart; }
    public List<Box> getBoxes() { return Collections.unmodifiableList(boxes); }
    public boolean getScanError() { return scanError; }
    public boolean getComplete() { return complete; }
    public String getReferencePart() { return target == Target.KANBAN ? standPart : kanbanPart; }

    public String comparison(String part) {
        if (complete) return "COMPLETE";
        if (part == null || part.isEmpty()) return "NOT_COMPARED";
        if (target == Target.STAND) return "REFERENCE";
        if (checkStand && (standPart == null || !partsMatch.test(standPart, part))) return "MISMATCH";
        if (target == Target.KANBAN) return checkStand ? "MATCH" : "REFERENCE";
        return kanbanPart != null && partsMatch.test(kanbanPart, part) ? "MATCH" : "MISMATCH";
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
            kanbanPart = part;
            target = Target.BOX_TAG;
        } else {
            boxes.add(new Box(boxes.size() + 1, part));
        }
        return true;
    }

    public boolean canFinish() {
        return !complete && !scanError && target == Target.BOX_TAG && kanbanPart != null && !boxes.isEmpty();
    }

    public boolean finish() {
        if (!canFinish()) return false;
        complete = true;
        return true;
    }

    public Target getLastRemovableTarget() {
        if (complete) return null;
        if (!boxes.isEmpty()) return Target.BOX_TAG;
        if (kanbanPart != null) return Target.KANBAN;
        if (standPart != null) return Target.STAND;
        return null;
    }

    public String getLastRemovablePart() {
        Target last = getLastRemovableTarget();
        if (last == Target.BOX_TAG) return boxes.get(boxes.size() - 1).partNo;
        if (last == Target.KANBAN) return kanbanPart;
        return last == Target.STAND ? standPart : null;
    }

    /** Undo only the last accepted item. Rejected scans remain recorded and must be corrected. */
    public boolean removeLast() {
        Target last = getLastRemovableTarget();
        if (last == null) return false;
        if (last == Target.BOX_TAG) {
            boxes.remove(boxes.size() - 1);
        } else if (last == Target.KANBAN) {
            kanbanPart = null;
            target = Target.KANBAN;
        } else {
            standPart = null;
            target = Target.STAND;
        }
        return true;
    }
}
