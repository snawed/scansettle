-- ScanSettle Basic (docs/architecture.md Section 11): £0/month, 0.35% per payment,
-- capped at £2.00. FREE/PRO/HOSPITALITY/ENTERPRISE are supported by the schema
-- (pricing_plan.code) but not seeded here — their commercial terms are not yet
-- defined, and inventing numbers for them would misrepresent a real decision.

insert into pricing_plan (code, fee_fraction, fee_cap_minor_units, monthly_subscription_minor_units, active)
values ('BASIC', 0.0035, 200, 0, true);
