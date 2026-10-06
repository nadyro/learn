-- Inventory: stock level per product (one row per product).
create table inventory_items
(
    product_id uuid        not null,
    on_hand    integer     not null,
    reserved   integer     not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version    bigint      not null,
    constraint pk_inventory_items primary key (product_id),
    constraint fk_inventory_items_product foreign key (product_id) references products (id),
    -- The database is the last line of defence: even a bug in the code cannot oversell.
    constraint ck_inventory_items_on_hand check (on_hand >= 0),
    constraint ck_inventory_items_reserved check (reserved >= 0),
    constraint ck_inventory_items_reserved_le_on_hand check (reserved <= on_hand)
);

-- Audit trail of manual stock changes. Append-only.
create table stock_adjustments
(
    id            uuid         not null,
    product_id    uuid         not null,
    delta         integer      not null,
    reason        varchar(30)  not null,
    note          varchar(500),
    performed_by  varchar(255) not null,
    on_hand_after integer      not null,
    created_at    timestamptz  not null,
    constraint pk_stock_adjustments primary key (id),
    constraint fk_stock_adjustments_inventory_item foreign key (product_id) references inventory_items (product_id),
    constraint ck_stock_adjustments_delta check (delta <> 0)
);

create index ix_stock_adjustments_product_created_at on stock_adjustments (product_id, created_at desc);
