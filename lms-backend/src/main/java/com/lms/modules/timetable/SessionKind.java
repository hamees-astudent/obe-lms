package com.lms.modules.timetable;

import java.util.ArrayList;
import java.util.List;

/** What a weekly class meeting is, which decides its length and the room it needs. */
public enum SessionKind {
    LECTURE,
    LAB;

    /**
     * The weekly meetings an offering needs, from its credit hours.
     *
     * <p>Up to three credit hours are theory: three hours a week is two 90-minute
     * lectures, and one or two credit hours is a single lecture. Every credit hour
     * beyond three is a lab credit, taught as one lab session a week — the 4-credit
     * courses in the catalog are "3 + 1" courses "with a weekly lab".
     */
    public static List<SessionKind> weeklySessions(int creditHours) {
        List<SessionKind> sessions = new ArrayList<>();
        int theory = Math.min(Math.max(creditHours, 1), 3);
        int lectures = theory == 3 ? 2 : 1;
        for (int i = 0; i < lectures; i++) sessions.add(LECTURE);
        for (int i = 3; i < creditHours; i++) sessions.add(LAB);
        return sessions;
    }
}
