-- Transactional outbox: events written with the business change, published to Kafka by OutboxRelay.
create table outbox_events
(
    id             uuid         not null,
    aggregate_type varchar(50)  not null,
    aggregate_id   varchar(100) not null,
    event_type     varchar(100) not null,
    payload        jsonb        not null,
    occurred_at    timestamptz  not null,
    published_at   timestamptz,
    attempts       integer      not null default 0,
    last_error     varchar(1000),
    constraint pk_outbox_events primary key (id)
);

-- Partial index: only unpublished rows are indexed, so it stays tiny however big the table grows.
create index ix_outbox_events_unpublished on outbox_events (occurred_at, id) where published_at is null;
