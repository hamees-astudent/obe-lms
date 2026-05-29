-- Spring Modulith JPA event publication store
-- Required by spring-modulith-starter-jpa for transactional outbox / event completion tracking.

CREATE TABLE event_publication (
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date
    ON event_publication (completion_date);
