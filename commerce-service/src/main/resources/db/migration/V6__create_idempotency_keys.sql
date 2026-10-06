-- Responses of idempotent requests (see IdempotencyService).
create table idempotency_keys
(
    id              uuid         not null,
    scope           varchar(300) not null,
    idempotency_key varchar(100) not null,
    request_hash    varchar(64)  not null,
    response_status integer,
    response_body   jsonb,
    created_at      timestamptz  not null,
    completed_at    timestamptz,
    constraint pk_idempotency_keys primary key (id),
    constraint uq_idempotency_keys_scope_key unique (scope, idempotency_key)
);

create index ix_idempotency_keys_created_at on idempotency_keys (created_at);
