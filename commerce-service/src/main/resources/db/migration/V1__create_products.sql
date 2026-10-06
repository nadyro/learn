-- Catalog: products sold in the shop.
create table products
(
    id             uuid           not null,
    sku            varchar(64)    not null,
    name           varchar(200)   not null,
    description    varchar(4000),
    price_amount   numeric(12, 2) not null,
    price_currency varchar(3)     not null,
    status         varchar(20)    not null,
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null,
    version        bigint         not null,
    constraint pk_products primary key (id),
    constraint uq_products_sku unique (sku),
    constraint ck_products_price_amount check (price_amount >= 0),
    constraint ck_products_status check (status in ('ACTIVE', 'ARCHIVED'))
);

-- Storefront listing: active products sorted by name.
create index ix_products_status_name on products (status, name);
