package com.lms.modules.timetable;

import com.lms.modules.timetable.TimetableDataRepository.OfferingRow;
import com.lms.modules.timetable.TimetableGenerator.ClassToPlace;
import com.lms.modules.timetable.TimetableGenerator.Placement;
import com.lms.modules.timetable.TimetableGenerator.RoomOption;
import com.lms.modules.timetable.TimetableGenerator.Unplaced;
import com.lms.modules.timetable.dto.CreateEntryRequest;
import com.lms.modules.timetable.dto.MoveEntryRequest;
import com.lms.modules.timetable.dto.TimetableEntryResponse;
import com.lms.modules.timetable.dto.TimetableGridResponse;
import com.lms.modules.timetable.dto.TimetableResponse;
import com.lms.modules.timetable.dto.UnscheduledOfferingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The weekly timetable of the current term.
 *
 * <p>The current term is every offering in an open semester, across all
 * programs. It is generated as one timetable, not one per program, because the
 * people and rooms are shared: a teacher of a first-year course in BS CS may
 * teach the same course to BS SE, and both classes need a room from one pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableEntryRepository entryRepository;
    private final RoomRepository roomRepository;
    private final TimetableDataRepository data;
    private final TimetableGrid grid;
    private final TimetableGenerator generator;

    // ── Reading ──────────────────────────────────────────────────────────────

    /** The whole current-term timetable, with the offerings it is short of. */
    public TimetableResponse getTimetable() {
        return build(data.findOpenOfferings(), Map.of(), true);
    }

    /** The current-term classes the user teaches, assists or is enrolled in. */
    public TimetableResponse getTimetableFor(UUID userId) {
        return build(data.findOpenOfferingsFor(userId), Map.of(), false);
    }

    // ── Generating ───────────────────────────────────────────────────────────

    /**
     * Replaces the current term's timetable with a newly generated one. Classes
     * of closed semesters are left as they were.
     */
    @Transactional
    public TimetableResponse generate() {
        long started = System.currentTimeMillis();
        List<OfferingRow> offerings = data.findOpenOfferings();
        Map<UUID, Set<UUID>> coTeachers = data.findCoTeachers();

        List<ClassToPlace> classes = offerings.stream()
                .map(o -> {
                    Set<UUID> teachers = new HashSet<>(coTeachers.getOrDefault(o.pscId(), Set.of()));
                    teachers.add(o.teacherId());
                    return new ClassToPlace(o.pscId(), teachers, o.students(),
                            SessionKind.weeklySessions(o.creditHours()));
                })
                .toList();
        List<RoomOption> rooms = roomRepository.findAllByActiveTrue().stream()
                .map(r -> new RoomOption(r.getId(), r.getCapacity(), r.getKind()))
                .toList();

        TimetableGenerator.Result result = generator.generate(classes, data.findSharedStudents(), rooms);

        List<UUID> pscIds = offerings.stream().map(OfferingRow::pscId).toList();
        if (!pscIds.isEmpty()) {
            entryRepository.deleteAllByPscIdIn(pscIds);
        }
        List<TimetableEntry> entries = new ArrayList<>();
        for (Placement p : result.placements()) {
            TimetableEntry e = new TimetableEntry();
            e.setPscId(p.pscId());
            e.setKind(p.kind());
            e.setRoomId(p.roomId());
            e.setDayOfWeek((short) p.day().getValue());
            e.setStartTime(grid.slots().get(p.firstSlot()).start());
            e.setEndTime(grid.endOf(p.firstSlot(), p.length()));
            entries.add(e);
        }
        entryRepository.saveAll(entries);
        entryRepository.flush();

        Map<UUID, String> reasons = new HashMap<>();
        for (Unplaced u : result.unplaced()) {
            reasons.merge(u.pscId(), u.reason(), (a, b) -> a.equals(b) ? a : a + " " + b);
        }
        log.info("Generated timetable: {} offerings, {} meetings placed, {} not placed, {} rooms, {} ms",
                offerings.size(), result.placements().size(), result.unplaced().size(), rooms.size(),
                System.currentTimeMillis() - started);
        return build(offerings, reasons, true);
    }

    /** Removes every class of the current term from the timetable. */
    @Transactional
    public void clear() {
        List<UUID> pscIds = data.findOpenOfferings().stream().map(OfferingRow::pscId).toList();
        if (!pscIds.isEmpty()) {
            entryRepository.deleteAllByPscIdIn(pscIds);
        }
    }

    // ── Editing by hand ──────────────────────────────────────────────────────

    @Transactional
    public TimetableEntryResponse createEntry(CreateEntryRequest req) {
        OfferingRow offering = requireOpenOffering(req.pscId());
        TimetableEntry entry = new TimetableEntry();
        entry.setPscId(offering.pscId());
        entry.setKind(req.kind());
        placeChecked(entry, offering, req.dayOfWeek(), req.startTime(), req.roomId());
        return toResponse(entryRepository.save(entry), offering, requireRoom(entry.getRoomId()).getName(), Set.of());
    }

    @Transactional
    public TimetableEntryResponse moveEntry(UUID entryId, MoveEntryRequest req) {
        TimetableEntry entry = requireEntry(entryId);
        OfferingRow offering = requireOpenOffering(entry.getPscId());
        placeChecked(entry, offering, req.dayOfWeek(), req.startTime(), req.roomId());
        return toResponse(entryRepository.save(entry), offering, requireRoom(entry.getRoomId()).getName(), Set.of());
    }

    @Transactional
    public void deleteEntry(UUID entryId) {
        TimetableEntry entry = requireEntry(entryId);
        requireOpenOffering(entry.getPscId());
        entryRepository.delete(entry);
    }

    /**
     * Sets the entry's day, time and room after checking it against the same
     * rules the generator keeps, so a hand edit cannot create a clash.
     */
    private void placeChecked(TimetableEntry entry, OfferingRow offering, int dayOfWeek,
                              LocalTime startTime, UUID roomId) {
        DayOfWeek day = DayOfWeek.of(dayOfWeek);
        if (!grid.days().contains(day)) {
            throw badRequest(dayName(day) + " is not a teaching day.");
        }
        TimetableGrid.Slot slot = grid.slotStartingAt(startTime)
                .orElseThrow(() -> badRequest("Classes start at the beginning of a period; "
                        + startTime + " is not one."));
        int length = grid.length(entry.getKind());
        if (!grid.canStart(slot.index(), length)) {
            throw badRequest("A lab takes " + length + " periods back to back, which do not fit from "
                    + startTime + ".");
        }
        LocalTime endTime = grid.endOf(slot.index(), length);

        Room room = requireRoom(roomId);
        if (!room.isActive()) {
            throw conflict(room.getName() + " is inactive.");
        }
        if (room.getKind() != entry.getKind()) {
            throw conflict(room.getName() + " is a " + kindLabel(room.getKind()) + "; this meeting needs a "
                    + kindLabel(entry.getKind()) + ".");
        }
        if (room.getCapacity() < offering.students()) {
            throw conflict(room.getName() + " seats " + room.getCapacity() + "; this class has "
                    + offering.students() + " students.");
        }

        Map<UUID, OfferingRow> open = data.findOpenOfferings().stream()
                .collect(Collectors.toMap(OfferingRow::pscId, Function.identity()));
        Map<UUID, Set<UUID>> coTeachers = data.findCoTeachers();
        Set<UUID> teachers = teachersOf(offering, coTeachers);
        Set<UUID> sharingStudents = data.findOfferingsSharingStudents(offering.pscId());
        Map<UUID, String> roomNames = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, Room::getName));

        for (TimetableEntry other : entryRepository.findAllByPscIdIn(open.keySet())) {
            if (other.getId() != null && other.getId().equals(entry.getId())) continue;
            if (other.getDayOfWeek() != day.getValue()) continue;
            OfferingRow o = open.get(other.getPscId());
            boolean sameOffering = other.getPscId().equals(offering.pscId());

            if (sameOffering && entry.getKind() == SessionKind.LECTURE && other.getKind() == SessionKind.LECTURE) {
                throw conflict(offering.courseCode() + " already has a lecture on " + dayName(day) + ".");
            }
            boolean overlaps = other.getStartTime().isBefore(endTime) && startTime.isBefore(other.getEndTime());
            if (!overlaps) continue;

            String when = dayName(day) + " " + other.getStartTime() + "–" + other.getEndTime();
            if (sameOffering) {
                throw conflict(offering.courseCode() + " already meets on " + when + ".");
            }
            if (other.getRoomId().equals(roomId)) {
                throw conflict(roomNames.get(roomId) + " is taken by " + label(o) + " on " + when + ".");
            }
            Set<UUID> otherTeachers = teachersOf(o, coTeachers);
            if (otherTeachers.stream().anyMatch(teachers::contains)) {
                throw conflict(o.teacherName() + " teaches " + label(o) + " on " + when + ".");
            }
            if (sharingStudents.contains(o.pscId())) {
                throw conflict("Students of " + offering.courseCode() + " have " + label(o) + " on " + when + ".");
            }
        }

        entry.setDayOfWeek((short) day.getValue());
        entry.setStartTime(startTime);
        entry.setEndTime(endTime);
        entry.setRoomId(roomId);
    }

    // ── Building responses ───────────────────────────────────────────────────

    private TimetableResponse build(List<OfferingRow> offerings, Map<UUID, String> reasons, boolean adminView) {
        Map<UUID, OfferingRow> byId = new LinkedHashMap<>();
        offerings.forEach(o -> byId.put(o.pscId(), o));
        List<TimetableEntry> entries = byId.isEmpty() ? List.of() : entryRepository.findAllByPscIdIn(byId.keySet());
        Map<UUID, String> roomNames = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, Room::getName));
        Map<UUID, Set<UUID>> cohorts = adminView ? data.findCohorts(byId.keySet()) : Map.of();

        List<TimetableEntryResponse> entryResponses = entries.stream()
                .sorted(Comparator.comparingInt(TimetableEntry::getDayOfWeek)
                        .thenComparing(TimetableEntry::getStartTime))
                .map(e -> toResponse(e, byId.get(e.getPscId()), roomNames.get(e.getRoomId()),
                        cohorts.getOrDefault(e.getPscId(), Set.of())))
                .toList();

        Map<UUID, List<SessionKind>> placed = entries.stream().collect(Collectors.groupingBy(
                TimetableEntry::getPscId, Collectors.mapping(TimetableEntry::getKind, Collectors.toList())));
        int required = 0;
        List<UnscheduledOfferingResponse> unscheduled = new ArrayList<>();
        for (OfferingRow o : offerings) {
            List<SessionKind> missing = new ArrayList<>(SessionKind.weeklySessions(o.creditHours()));
            required += missing.size();
            placed.getOrDefault(o.pscId(), List.of()).forEach(missing::remove);
            if (!missing.isEmpty() && adminView) {
                unscheduled.add(new UnscheduledOfferingResponse(o.pscId(), o.courseCode(), o.courseName(),
                        o.programName(), o.teacherName(), o.students(), missing, reasons.get(o.pscId())));
            }
        }
        return new TimetableResponse(gridResponse(), entryResponses, unscheduled,
                offerings.size(), required, entries.size());
    }

    private TimetableGridResponse gridResponse() {
        return new TimetableGridResponse(
                grid.days().stream()
                        .map(d -> new TimetableGridResponse.Day(d.getValue(), dayName(d)))
                        .toList(),
                grid.slots().stream()
                        .map(s -> new TimetableGridResponse.Slot(s.index(), s.start(), s.end()))
                        .toList(),
                grid.labSlots());
    }

    private static TimetableEntryResponse toResponse(TimetableEntry e, OfferingRow o, String roomName,
                                                     Set<UUID> cohortIds) {
        return new TimetableEntryResponse(e.getId(), e.getPscId(), o.courseCode(), o.courseName(),
                o.programId(), o.programName(), o.semesterName(), o.teacherId(), o.teacherName(),
                e.getRoomId(), roomName, e.getKind(), e.getDayOfWeek(), e.getStartTime(), e.getEndTime(),
                o.students(), cohortIds);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Set<UUID> teachersOf(OfferingRow o, Map<UUID, Set<UUID>> coTeachers) {
        Set<UUID> teachers = new HashSet<>(coTeachers.getOrDefault(o.pscId(), Set.of()));
        teachers.add(o.teacherId());
        return teachers;
    }

    private OfferingRow requireOpenOffering(UUID pscId) {
        return data.findOpenOfferings().stream()
                .filter(o -> o.pscId().equals(pscId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No offering " + pscId + " in an open semester. Only the current term's "
                                + "timetable can be changed."));
    }

    private TimetableEntry requireEntry(UUID id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Timetable entry not found: " + id));
    }

    private Room requireRoom(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
    }

    private static String label(OfferingRow o) {
        return o.courseCode() + " (" + o.programName() + ")";
    }

    private static String kindLabel(SessionKind kind) {
        return kind == SessionKind.LAB ? "lab" : "lecture room";
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
