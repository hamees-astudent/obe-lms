package com.lms.modules.timetable;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.timetable.dto.CreateEntryRequest;
import com.lms.modules.timetable.dto.MoveEntryRequest;
import com.lms.modules.timetable.dto.RoomRequest;
import com.lms.modules.timetable.dto.RoomResponse;
import com.lms.modules.timetable.dto.TimetableEntryResponse;
import com.lms.modules.timetable.dto.TimetableResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Rooms and the current term's timetable (admin), and each user's own week. */
@RestController
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final RoomService roomService;

    // ── Own timetable ─────────────────────────────────────────────────────────

    @GetMapping("/api/me/timetable")
    @PreAuthorize("isAuthenticated()")
    public TimetableResponse myTimetable(@AuthenticationPrincipal UserPrincipal principal) {
        return timetableService.getTimetableFor(principal.getId());
    }

    // ── Admin: timetable ──────────────────────────────────────────────────────

    @GetMapping("/api/admin/timetable")
    @PreAuthorize("hasRole('ADMIN')")
    public TimetableResponse timetable() {
        return timetableService.getTimetable();
    }

    @PostMapping("/api/admin/timetable/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public TimetableResponse generate() {
        return timetableService.generate();
    }

    @DeleteMapping("/api/admin/timetable")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        timetableService.clear();
    }

    @PostMapping("/api/admin/timetable/entries")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableEntryResponse createEntry(@Valid @RequestBody CreateEntryRequest req) {
        return timetableService.createEntry(req);
    }

    @PutMapping("/api/admin/timetable/entries/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TimetableEntryResponse moveEntry(@PathVariable UUID id, @Valid @RequestBody MoveEntryRequest req) {
        return timetableService.moveEntry(id, req);
    }

    @DeleteMapping("/api/admin/timetable/entries/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id) {
        timetableService.deleteEntry(id);
    }

    // ── Admin: rooms ──────────────────────────────────────────────────────────

    @GetMapping("/api/admin/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoomResponse> rooms() {
        return roomService.list();
    }

    @PostMapping("/api/admin/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody RoomRequest req) {
        return roomService.create(req);
    }

    @PutMapping("/api/admin/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomRequest req) {
        return roomService.update(id, req);
    }

    @DeleteMapping("/api/admin/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable UUID id) {
        roomService.delete(id);
    }
}
