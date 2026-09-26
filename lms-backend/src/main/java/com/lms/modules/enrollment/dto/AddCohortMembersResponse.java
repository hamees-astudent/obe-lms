package com.lms.modules.enrollment.dto;

import java.util.List;

/**
 * What an add-members request did. Unmatched input is reported rather than
 * rejected, so one typo in a pasted list of sixty roll numbers does not
 * throw away the other fifty-nine.
 */
public record AddCohortMembersResponse(
        int added,
        int alreadyMembers,
        /** Ids or roll numbers that matched no user. */
        List<String> notFound,
        /** Matched users who are not students, as "name (ROLE)". */
        List<String> notStudents,
        CohortDetailResponse cohort
) {}
