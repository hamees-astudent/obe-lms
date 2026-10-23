package com.lms.shared.events;

import java.util.UUID;

/**
 * A course material was created or edited — published in-process (Spring
 * application event, persisted by Spring Modulith's event registry) so the
 * study assistant can read and index the new file after the transaction
 * commits. Deletions need no event: the index rows cascade with the material.
 *
 * @param objectKey the stored file for DOCUMENT materials, else null
 */
public record MaterialChangedEvent(UUID materialId, UUID pscId, String type, String objectKey) {}
