-- Phase 9: automated velocity checks raise fraud flags too, not just ops staff —
-- those have no platform_admin_user to attribute, so raised_by becomes optional
-- (null means "raised by the system", distinguished in the API/UI, not a new column).
alter table fraud_flag alter column raised_by drop not null;
