package com.lms.modules.timetable;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a weekly timetable: a day, period and room for every class meeting,
 * with nobody in two places at once.
 *
 * <p>Hard rules — a timetable never breaks these:
 * <ul>
 *   <li>a teacher teaches one class at a time;</li>
 *   <li>two offerings that share even one student never meet at the same time;</li>
 *   <li>a room holds one class at a time, is of the right kind (lecture room or
 *       lab), and seats the whole roster;</li>
 *   <li>an offering has at most one lecture a day.</li>
 * </ul>
 *
 * <p>Preferences, weighed when choosing between legal places: lectures of one
 * course on non-adjacent days (Mon/Wed rather than Mon/Tue), a lab on a day of
 * its own, each student's and teacher's week spread evenly across days, few
 * classes in the last period, and the smallest room that fits.
 *
 * <p>Method: the hardest classes are placed first (labs, then offerings that
 * clash with the most others), each meeting going to the cheapest legal place.
 * That is repeated with the order varied and the best timetable — fewest
 * meetings left out, then lowest cost — is kept. Randomness comes from a fixed
 * seed, so the same data always gives the same timetable.
 *
 * <p>Pure computation: no Spring, no database.
 */
public class TimetableGenerator {

    /** An offering to timetable. {@code teacherIds} are the people who must be there to teach it. */
    public record ClassToPlace(UUID pscId, Set<UUID> teacherIds, int students, List<SessionKind> sessions) {}

    public record RoomOption(UUID id, int capacity, SessionKind kind) {}

    public record Placement(UUID pscId, SessionKind kind, DayOfWeek day, int firstSlot, int length, UUID roomId) {}

    public record Unplaced(UUID pscId, SessionKind kind, String reason) {}

    public record Result(List<Placement> placements, List<Unplaced> unplaced, double cost) {}

    private static final long SEED = 20260926L;

    private static final double LAB_ON_TEACHING_DAY = 30;
    private static final double ADJACENT_LECTURE_DAYS = 6;
    private static final double SHARED_DAY_LOAD = 1.5;
    private static final double LAST_PERIOD = 1;
    private static final double EMPTY_SEAT = 0.01;

    private final TimetableGrid grid;
    private final int attempts;

    public TimetableGenerator(TimetableGrid grid, int attempts) {
        this.grid = grid;
        this.attempts = Math.max(1, attempts);
    }

    /**
     * @param classes        in a stable order; the same input gives the same timetable
     * @param sharedStudents for each offering, the offerings it shares at least one student with
     */
    public Result generate(List<ClassToPlace> classes, Map<UUID, Set<UUID>> sharedStudents,
                           List<RoomOption> rooms) {
        Problem problem = new Problem(classes, sharedStudents, rooms);
        Random rnd = new Random(SEED);
        Result best = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Result result = new Attempt(problem, rnd, attempt > 0).run();
            if (best == null
                    || result.unplaced().size() < best.unplaced().size()
                    || result.unplaced().size() == best.unplaced().size() && result.cost() < best.cost()) {
                best = result;
            }
            if (best.unplaced().isEmpty() && attempt >= attempts / 2) break;
        }
        return best;
    }

    // ── The problem, indexed ────────────────────────────────────────────────

    private final class Problem {
        final List<ClassToPlace> classes;
        final List<RoomOption> rooms;
        /** clashes[i].get(j): offerings i and j share a teacher or a student. */
        final BitSet[] clashes;
        final double[] difficulty;

        Problem(List<ClassToPlace> classes, Map<UUID, Set<UUID>> sharedStudents, List<RoomOption> rooms) {
            this.classes = List.copyOf(classes);
            this.rooms = rooms.stream()
                    .sorted(Comparator.comparingInt(RoomOption::capacity).thenComparing(r -> r.id().toString()))
                    .toList();
            int n = classes.size();
            Map<UUID, Integer> index = new HashMap<>();
            for (int i = 0; i < n; i++) index.put(classes.get(i).pscId(), i);

            clashes = new BitSet[n];
            for (int i = 0; i < n; i++) clashes[i] = new BitSet(n);
            for (int i = 0; i < n; i++) {
                for (UUID other : sharedStudents.getOrDefault(classes.get(i).pscId(), Set.of())) {
                    Integer j = index.get(other);
                    if (j != null && j != i) {
                        clashes[i].set(j);
                        clashes[j].set(i);
                    }
                }
                for (int j = i + 1; j < n; j++) {
                    if (shareAny(classes.get(i).teacherIds(), classes.get(j).teacherIds())) {
                        clashes[i].set(j);
                        clashes[j].set(i);
                    }
                }
            }

            difficulty = new double[n];
            for (int i = 0; i < n; i++) {
                ClassToPlace c = classes.get(i);
                long labs = c.sessions().stream().filter(k -> k == SessionKind.LAB).count();
                difficulty[i] = labs * 10 + c.sessions().size() * 2 + clashes[i].cardinality();
            }
        }

        private static boolean shareAny(Set<UUID> a, Set<UUID> b) {
            for (UUID x : a) if (b.contains(x)) return true;
            return false;
        }
    }

    // ── One attempt ─────────────────────────────────────────────────────────

    private final class Attempt {
        private final Problem p;
        private final Random rnd;
        private final boolean shuffle;
        private final int days = grid.days().size();
        private final int slots = grid.slots().size();

        /** occupants[d][s]: offerings meeting in that period. */
        private final List<List<Integer>> occupants = new ArrayList<>();
        private final boolean[][][] roomBusy;
        /** sessionsOnDay[i][d], lecturesOnDay[i][d] */
        private final int[][] sessionsOnDay;
        private final int[][] lecturesOnDay;

        private final List<Placement> placements = new ArrayList<>();
        private final List<Unplaced> unplaced = new ArrayList<>();
        private double cost;

        Attempt(Problem p, Random rnd, boolean shuffle) {
            this.p = p;
            this.rnd = rnd;
            this.shuffle = shuffle;
            for (int c = 0; c < days * slots; c++) occupants.add(new ArrayList<>());
            roomBusy = new boolean[p.rooms.size()][days][slots];
            sessionsOnDay = new int[p.classes.size()][days];
            lecturesOnDay = new int[p.classes.size()][days];
        }

        Result run() {
            List<Integer> order = new ArrayList<>();
            double[] key = new double[p.classes.size()];
            for (int i = 0; i < p.classes.size(); i++) {
                order.add(i);
                key[i] = p.difficulty[i] * (shuffle ? 0.6 + rnd.nextDouble() * 0.8 : 1);
            }
            order.sort(Comparator.comparingDouble((Integer i) -> -key[i]).thenComparingInt(i -> i));

            for (int i : order) {
                ClassToPlace c = p.classes.get(i);
                // Labs first: they need two free periods in a row and a scarcer room.
                List<SessionKind> sessions = new ArrayList<>(c.sessions());
                sessions.sort(Comparator.comparing((SessionKind k) -> k != SessionKind.LAB));
                for (SessionKind kind : sessions) place(i, kind);
            }
            return new Result(placements, unplaced, cost);
        }

        private void place(int i, SessionKind kind) {
            ClassToPlace c = p.classes.get(i);
            int length = grid.length(kind);
            double bestCost = Double.MAX_VALUE;
            int bestDay = -1, bestSlot = -1, bestRoom = -1;

            for (int d = 0; d < days; d++) {
                if (kind == SessionKind.LECTURE && lecturesOnDay[i][d] > 0) continue;
                for (int s = 0; s < slots; s++) {
                    if (!grid.canStart(s, length) || !peopleFree(i, d, s, length)) continue;
                    int room = smallestFreeRoom(c, kind, d, s, length);
                    if (room < 0) continue;
                    double cost = cost(i, kind, d, s, length, room);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestDay = d;
                        bestSlot = s;
                        bestRoom = room;
                    }
                }
            }

            if (bestDay < 0) {
                unplaced.add(new Unplaced(c.pscId(), kind, whyNot(c, kind)));
                return;
            }
            for (int s = bestSlot; s < bestSlot + length; s++) {
                occupants.get(bestDay * slots + s).add(i);
                roomBusy[bestRoom][bestDay][s] = true;
            }
            sessionsOnDay[i][bestDay]++;
            if (kind == SessionKind.LECTURE) lecturesOnDay[i][bestDay]++;
            cost += bestCost;
            placements.add(new Placement(c.pscId(), kind, grid.days().get(bestDay), bestSlot, length,
                    p.rooms.get(bestRoom).id()));
        }

        /** No teacher or student of offering i is busy, nor is i itself, in those periods. */
        private boolean peopleFree(int i, int d, int first, int length) {
            for (int s = first; s < first + length; s++) {
                for (int j : occupants.get(d * slots + s)) {
                    if (j == i || p.clashes[i].get(j)) return false;
                }
            }
            return true;
        }

        private int smallestFreeRoom(ClassToPlace c, SessionKind kind, int d, int first, int length) {
            rooms:
            for (int r = 0; r < p.rooms.size(); r++) {
                RoomOption room = p.rooms.get(r);
                if (room.kind() != kind || room.capacity() < c.students()) continue;
                for (int s = first; s < first + length; s++) {
                    if (roomBusy[r][d][s]) continue rooms;
                }
                return r;   // rooms are sorted smallest first
            }
            return -1;
        }

        private double cost(int i, SessionKind kind, int d, int first, int length, int room) {
            double cost = 0;
            if (kind == SessionKind.LAB && sessionsOnDay[i][d] > 0) cost += LAB_ON_TEACHING_DAY;
            if (kind == SessionKind.LECTURE) {
                if (d > 0 && lecturesOnDay[i][d - 1] > 0) cost += ADJACENT_LECTURE_DAYS;
                if (d + 1 < days && lecturesOnDay[i][d + 1] > 0) cost += ADJACENT_LECTURE_DAYS;
            }
            // Offerings sharing students or a teacher with this one already on that day:
            // piling onto the same day makes someone's day long and another day empty.
            BitSet clashes = p.clashes[i];
            for (int j = clashes.nextSetBit(0); j >= 0; j = clashes.nextSetBit(j + 1)) {
                cost += SHARED_DAY_LOAD * sessionsOnDay[j][d];
            }
            if (first + length == slots) cost += LAST_PERIOD;
            cost += EMPTY_SEAT * (p.rooms.get(room).capacity() - p.classes.get(i).students());
            if (shuffle) cost += rnd.nextDouble() * 0.5;
            return cost;
        }

        private String whyNot(ClassToPlace c, SessionKind kind) {
            String roomKind = kind == SessionKind.LAB ? "lab" : "lecture room";
            boolean anyOfKind = p.rooms.stream().anyMatch(r -> r.kind() == kind);
            if (!anyOfKind) {
                return "There is no active " + roomKind + ". Add one under Rooms.";
            }
            boolean anyBigEnough = p.rooms.stream()
                    .anyMatch(r -> r.kind() == kind && r.capacity() >= c.students());
            if (!anyBigEnough) {
                return "No active " + roomKind + " seats " + c.students() + " students.";
            }
            return "No free period where the teacher, every enrolled student and a suitable "
                    + roomKind + " are all available.";
        }
    }
}
