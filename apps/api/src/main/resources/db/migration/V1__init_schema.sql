-- ScanSettle initial schema.
-- Mirrors docs/domain-model.md exactly (including the deviations recorded in
-- docs/decisions/0001-0004): PaymentLink/Payment split, no QRCode or Tip tables,
-- Venue added, BillPaymentReservation for concurrency-safe Tables payments,
-- Refund scoped to a request/record.
--
-- This migration lays down the full schema now, ahead of the JPA entities/services
-- for most of it (those land module-by-module from Phase 3 onward) — schema is
-- structural foundation, not business logic, and having it settled avoids a
-- disruptive migration once merchants have real data.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Pricing
-- ---------------------------------------------------------------------------

create table pricing_plan (
    id                                  uuid primary key default gen_random_uuid(),
    code                                varchar(32) not null unique,
    fee_fraction                        numeric(6, 5) not null,
    fee_cap_minor_units                 bigint not null,
    monthly_subscription_minor_units    bigint not null,
    active                              boolean not null default true
);

-- ---------------------------------------------------------------------------
-- Merchant
-- ---------------------------------------------------------------------------

create table merchant (
    id                      uuid primary key default gen_random_uuid(),
    legal_name              varchar(255) not null,
    trading_name            varchar(255) not null,
    business_type           varchar(64) not null,
    verification_status     varchar(32) not null default 'UNVERIFIED',
    pricing_plan_id         uuid not null references pricing_plan(id),
    status                  varchar(32) not null default 'ACTIVE',
    created_at              timestamptz not null default now()
);

create table merchant_user (
    id              uuid primary key default gen_random_uuid(),
    merchant_id     uuid not null references merchant(id),
    email           varchar(255) not null,
    password_hash   varchar(255) not null,
    role            varchar(32) not null,
    mfa_enabled     boolean not null default false,
    status          varchar(32) not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    unique (merchant_id, email)
);
create index idx_merchant_user_merchant on merchant_user(merchant_id);

create table merchant_bank_account (
    id                      uuid primary key default gen_random_uuid(),
    merchant_id             uuid not null references merchant(id),
    sort_code_encrypted     varchar(255) not null,
    account_number_encrypted varchar(255) not null,
    account_name            varchar(255) not null,
    verified                boolean not null default false,
    status                  varchar(32) not null default 'ACTIVE',
    created_at              timestamptz not null default now()
);
create index idx_merchant_bank_account_merchant on merchant_bank_account(merchant_id);

create table venue (
    id              uuid primary key default gen_random_uuid(),
    merchant_id     uuid not null references merchant(id),
    name            varchar(255) not null,
    address         text,
    timezone        varchar(64) not null default 'Europe/London',
    created_at      timestamptz not null default now()
);
create index idx_venue_merchant on venue(merchant_id);

-- ---------------------------------------------------------------------------
-- ScanSettle Payments (trade/professional)
-- ---------------------------------------------------------------------------

create table payment_link (
    id              uuid primary key default gen_random_uuid(),
    merchant_id     uuid not null references merchant(id),
    amount_minor_units bigint not null check (amount_minor_units > 0),
    currency_code   varchar(3) not null default 'GBP',
    description     varchar(500) not null,
    reference       varchar(100) not null,
    status          varchar(32) not null default 'ACTIVE',
    expires_at      timestamptz,
    created_by      uuid references merchant_user(id),
    created_at      timestamptz not null default now()
);
create index idx_payment_link_merchant on payment_link(merchant_id);

create table bill (
    id                  uuid primary key default gen_random_uuid(),
    venue_id            uuid not null references venue(id),
    table_id            uuid, -- FK added after dining_table is created below
    pos_reference        varchar(255),
    total_amount_minor_units bigint not null check (total_amount_minor_units >= 0),
    currency_code       varchar(3) not null default 'GBP',
    state               varchar(32) not null default 'OPEN',
    opened_at           timestamptz not null default now(),
    closed_at           timestamptz
);

create table payment (
    id                      uuid primary key default gen_random_uuid(),
    merchant_id             uuid not null references merchant(id),
    payment_link_id         uuid references payment_link(id),
    bill_payment_id         uuid, -- FK added after bill_payment is created below
    amount_minor_units      bigint not null check (amount_minor_units > 0),
    currency_code           varchar(3) not null default 'GBP',
    state                   varchar(32) not null default 'CREATED',
    payer_contact           varchar(255),
    idempotency_key         varchar(128),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    check (
        (payment_link_id is not null and bill_payment_id is null)
        or (payment_link_id is null and bill_payment_id is not null)
        or (payment_link_id is null and bill_payment_id is null)
    )
);
create index idx_payment_merchant on payment(merchant_id);
create index idx_payment_payment_link on payment(payment_link_id);
create unique index uq_payment_idempotency on payment(merchant_id, idempotency_key) where idempotency_key is not null;

create table provider_transaction (
    id                  uuid primary key default gen_random_uuid(),
    payment_id          uuid unique references payment(id),
    bill_payment_id     uuid, -- set for Tables payments; FK added after bill_payment below
    provider             varchar(64) not null,
    provider_reference    varchar(255) not null,
    raw_status           varchar(64) not null,
    last_synced_at       timestamptz not null default now(),
    unique (provider, provider_reference)
);

create table refund (
    id              uuid primary key default gen_random_uuid(),
    payment_id      uuid not null references payment(id),
    requested_by    uuid references merchant_user(id),
    amount_minor_units bigint not null check (amount_minor_units > 0),
    status          varchar(32) not null default 'REQUESTED',
    note            text,
    resolved_at     timestamptz,
    created_at      timestamptz not null default now()
);
create index idx_refund_payment on refund(payment_id);

-- ---------------------------------------------------------------------------
-- ScanSettle Tables (hospitality)
-- ---------------------------------------------------------------------------

create table dining_table (
    id              uuid primary key default gen_random_uuid(),
    venue_id        uuid not null references venue(id),
    label           varchar(64) not null,
    qr_token        varchar(64) not null unique,
    status          varchar(32) not null default 'ACTIVE'
);
create index idx_dining_table_venue on dining_table(venue_id);

alter table bill add constraint fk_bill_table foreign key (table_id) references dining_table(id);
create index idx_bill_venue on bill(venue_id);
create index idx_bill_table on bill(table_id);

create table bill_line_item (
    id              uuid primary key default gen_random_uuid(),
    bill_id         uuid not null references bill(id),
    description     varchar(255) not null,
    amount_minor_units bigint not null check (amount_minor_units >= 0)
);
create index idx_bill_line_item_bill on bill_line_item(bill_id);

create table bill_payment (
    id                          uuid primary key default gen_random_uuid(),
    bill_id                     uuid not null references bill(id),
    contribution_amount_minor_units bigint not null check (contribution_amount_minor_units >= 0),
    tip_amount_minor_units      bigint not null default 0 check (tip_amount_minor_units >= 0),
    tip_method                  varchar(16) not null default 'NONE',
    state                       varchar(32) not null default 'CREATED',
    payer_contact               varchar(255),
    created_at                  timestamptz not null default now()
);
create index idx_bill_payment_bill on bill_payment(bill_id);

alter table payment add constraint fk_payment_bill_payment foreign key (bill_payment_id) references bill_payment(id);
alter table provider_transaction add constraint fk_provider_transaction_bill_payment foreign key (bill_payment_id) references bill_payment(id);
alter table provider_transaction add constraint uq_provider_transaction_bill_payment unique (bill_payment_id);

create table bill_payment_reservation (
    id                  uuid primary key default gen_random_uuid(),
    bill_id             uuid not null references bill(id),
    requested_amount_minor_units bigint not null check (requested_amount_minor_units > 0),
    status              varchar(16) not null default 'ACTIVE',
    expires_at          timestamptz not null,
    bill_payment_id     uuid references bill_payment(id),
    created_at          timestamptz not null default now()
);
create index idx_bill_payment_reservation_bill on bill_payment_reservation(bill_id);
create index idx_bill_payment_reservation_status on bill_payment_reservation(status);

-- ---------------------------------------------------------------------------
-- POS
-- ---------------------------------------------------------------------------

create table pos_connection (
    id              uuid primary key default gen_random_uuid(),
    venue_id        uuid not null references venue(id),
    provider        varchar(64) not null,
    status          varchar(32) not null default 'INACTIVE',
    config          jsonb not null default '{}'::jsonb
);
create index idx_pos_connection_venue on pos_connection(venue_id);

-- ---------------------------------------------------------------------------
-- Cross-cutting
-- ---------------------------------------------------------------------------

create table webhook_event (
    id                  uuid primary key default gen_random_uuid(),
    source              varchar(32) not null, -- OPEN_BANKING | POS
    provider            varchar(64) not null,
    provider_event_id   varchar(255) not null,
    signature_valid     boolean not null,
    payload             jsonb not null,
    received_at         timestamptz not null default now(),
    processed_at        timestamptz,
    processing_result   varchar(64),
    unique (provider, provider_event_id)
);

create table reconciliation_record (
    id                          uuid primary key default gen_random_uuid(),
    payment_id                  uuid references payment(id),
    bill_payment_id              uuid references bill_payment(id),
    provider_transaction_id      uuid references provider_transaction(id),
    expected_amount_minor_units  bigint not null,
    confirmed_amount_minor_units bigint,
    matched                     boolean not null default false,
    discrepancy_note            text,
    created_at                  timestamptz not null default now(),
    check (
        (payment_id is not null and bill_payment_id is null)
        or (payment_id is null and bill_payment_id is not null)
    )
);

create table audit_event (
    id              uuid primary key default gen_random_uuid(),
    merchant_id     uuid references merchant(id),
    actor_type      varchar(32) not null, -- MERCHANT_USER | CUSTOMER | SYSTEM | OPS
    actor_id        varchar(255),
    action          varchar(128) not null,
    entity_type     varchar(64) not null,
    entity_id       varchar(255) not null,
    before_state    jsonb,
    after_state     jsonb,
    correlation_id  varchar(64),
    occurred_at     timestamptz not null default now()
);
create index idx_audit_event_merchant on audit_event(merchant_id);
create index idx_audit_event_entity on audit_event(entity_type, entity_id);

create table fee_ledger_entry (
    id                      uuid primary key default gen_random_uuid(),
    payment_id              uuid references payment(id),
    bill_payment_id         uuid references bill_payment(id),
    merchant_id             uuid not null references merchant(id),
    pricing_plan_id         uuid not null references pricing_plan(id),
    calculated_fee_minor_units bigint not null,
    created_at              timestamptz not null default now(),
    check (
        (payment_id is not null and bill_payment_id is null)
        or (payment_id is null and bill_payment_id is not null)
    )
);
create index idx_fee_ledger_entry_merchant on fee_ledger_entry(merchant_id);

create table idempotency_key (
    key                 varchar(128) not null,
    merchant_id         uuid references merchant(id),
    endpoint            varchar(255) not null,
    request_hash        varchar(128) not null,
    response_snapshot   jsonb,
    created_at          timestamptz not null default now(),
    primary key (key, endpoint)
);
