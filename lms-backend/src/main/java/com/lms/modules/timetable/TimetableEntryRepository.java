package com.lms.modules.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    List<TimetableEntry> findAllByPscIdIn(Collection<UUID> pscIds);

    boolean existsByRoomId(UUID roomId);

    /** [roomId, meetings] for every room that has any. */
    @Query("SELECT e.roomId, COUNT(e) FROM TimetableEntry e GROUP BY e.roomId")
    List<Object[]> countByRoom();

    @Modifying
    @Query("DELETE FROM TimetableEntry e WHERE e.pscId IN :pscIds")
    int deleteAllByPscIdIn(@Param("pscIds") Collection<UUID> pscIds);
}
