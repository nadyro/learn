-- Human-friendly order numbers (KO-00001000, KO-00001001, ...). Sequences never roll back, so gaps are expected.
create sequence order_number_seq start with 1000 increment by 1;

create table orders
(
    id                  uuid           not null,
    order_number        varchar(20)    not null,
    customer_id         uuid           not null,
    status              varchar(32)    not null,
    currency            varchar(3)     not null,
    total_amount        numeric(12, 2) not null,
    ship_recipient_name varchar(100)   not null,
    ship_line1          varchar(200)   not null,
    ship_line2          varchar(200),
    ship_city           varchar(100)   not null,
    ship_postal_code    varchar(20)    not null,
    ship_country_code   varchar(2)     not null,
    payment_reference   varchar(100),
    carrier             varchar(50),
    tracking_number     varchar(100),
    cancellation_reason varchar(30),
    placed_at           timestamptz    not null,
    paid_at             timestamptz,
    shipped_at          timestamptz,
    delivered_at        timestamptz,
    cancelled_at        timestamptz,
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null,
    version             bigint         not null,
    constraint pk_orders primary key (id),
    constraint uq_orders_order_number unique (order_number),
    constraint fk_orders_customer foreign key (customer_id) references customers (id),
    constraint ck_orders_status check (status in ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    constraint ck_orders_total_amount check (total_amount >= 0)
);

-- "My orders" page: a customer's orders, newest first.
create index ix_orders_customer_placed_at on orders (customer_id, placed_at desc);
-- Back-office search by status.
create index ix_orders_status_placed_at on orders (status, placed_at desc);

create table order_lines
(
    id           uuid           not null,
    order_id     uuid           not null,
    line_number  integer        not null,
    product_id   uuid           not null,
    sku          varchar(64)    not null,
    product_name varchar(200)   not null,
    unit_price   numeric(12, 2) not null,
    quantity     integer        not null,
    line_total   numeric(12, 2) not null,
    constraint pk_order_lines primary key (id),
    constraint fk_order_lines_order foreign key (order_id) references orders (id),
    constraint fk_order_lines_product foreign key (product_id) references products (id),
    constraint uq_order_lines_order_line_number unique (order_id, line_number),
    constraint ck_order_lines_quantity check (quantity > 0)
);

-- Postgres does not index foreign keys automatically.
create index ix_order_lines_product_id on order_lines (product_id);
