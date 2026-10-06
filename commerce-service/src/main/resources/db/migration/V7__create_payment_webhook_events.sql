-- Payment provider events already processed, for webhook deduplication.
create table payment_webhook_events
(
    event_id     varchar(100) not null,
    event_type   varchar(100) not null,
    processed_at timestamptz  not null,
    constraint pk_payment_webhook_events primary key (event_id)
);
