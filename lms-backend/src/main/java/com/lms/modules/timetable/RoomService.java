package com.lms.modules.timetable;

import com.lms.modules.timetable.dto.RoomRequest;
import com.lms.modules.timetable.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rooms the timetable places classes in. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final TimetableEntryRepository entryRepository;

    public List<RoomResponse> list() {
        Map<UUID, Long> usage = new HashMap<>();
        for (Object[] row : entryRepository.countByRoom()) {
            usage.put((UUID) row[0], (Long) row[1]);
        }
        return roomRepository.findAllByOrderByKindAscNameAsc().stream()
                .map(r -> toResponse(r, usage.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        String name = req.name().trim();
        if (roomRepository.existsByNameIgnoreCase(name)) {
            throw duplicateName(name);
        }
        Room room = new Room();
        apply(room, req, name);
        return toResponse(roomRepository.save(room), 0);
    }

    @Transactional
    public RoomResponse update(UUID id, RoomRequest req) {
        Room room = requireRoom(id);
        String name = req.name().trim();
        if (roomRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName(name);
        }
        apply(room, req, name);
        long used = entryRepository.countByRoom().stream()
                .filter(row -> id.equals(row[0])).mapToLong(row -> (Long) row[1]).sum();
        return toResponse(roomRepository.save(room), used);
    }

    /** Refused while any class meets in the room: its timetable would lose them silently. */
    @Transactional
    public void delete(UUID id) {
        Room room = requireRoom(id);
        if (entryRepository.existsByRoomId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Classes are timetabled in " + room.getName() + ". Move them, regenerate the timetable "
                            + "after deactivating the room, or keep the room and mark it inactive.");
        }
        roomRepository.delete(room);
    }

    private static void apply(Room room, RoomRequest req, String name) {
        room.setName(name);
        room.setBuilding(req.building() == null || req.building().isBlank() ? null : req.building().trim());
        room.setCapacity(req.capacity());
        room.setKind(req.kind());
        room.setActive(req.active() == null || req.active());
    }

    private Room requireRoom(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
    }

    private static ResponseStatusException duplicateName(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A room named \"" + name + "\" already exists");
    }

    static RoomResponse toResponse(Room r, long weeklyClasses) {
        return new RoomResponse(r.getId(), r.getName(), r.getBuilding(), r.getCapacity(), r.getKind(),
                r.isActive(), weeklyClasses);
    }
}
