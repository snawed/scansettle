-- Phase 8: Admin, Reconciliation & Operations. ScanSettle's own internal ops/support
-- staff are a genuinely separate persona from merchant users (docs/architecture.md
-- Section 4, "ScanSettle Ops/Support") — never scoped to a single merchant, so they
-- get their own login/table rather than a role bolted onto merchant_user.

create table platform_admin_user (
    id              uuid primary key default gen_random_uuid(),
    email           varchar(255) not null unique,
    password_hash   varchar(255) not null,
    created_at      timestamptz not null default now()
);

-- Lets an admin/ops query pull every webhook attempt for a given provider reference
-- directly, instead of parsing the jsonb payload per row.
alter table webhook_event add column provider_reference varchar(255);
create index idx_webhook_event_provider_reference on webhook_event(provider_reference);

create table fraud_flag (
    id              uuid primary key default gen_random_uuid(),
    merchant_id     uuid references merchant(id),
    payment_id      uuid references payment(id),
    reason          text not null,
    status          varchar(32) not null default 'ACTIVE',
    raised_by       uuid not null references platform_admin_user(id),
    raised_at       timestamptz not null default now(),
    cleared_by      uuid references platform_admin_user(id),
    cleared_at      timestamptz
);
create index idx_fraud_flag_merchant on fraud_flag(merchant_id);
create index idx_fraud_flag_payment on fraud_flag(payment_id);
