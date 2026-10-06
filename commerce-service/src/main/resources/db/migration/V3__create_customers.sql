-- Customers, provisioned on first login from the identity provider.
create table customers
(
    id         uuid         not null,
    subject    varchar(255) not null, -- "sub" claim of the access token
    email      varchar(320),
    first_name varchar(100),
    last_name  varchar(100),
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    version    bigint       not null,
    constraint pk_customers primary key (id),
    constraint uq_customers_subject unique (subject)
);
