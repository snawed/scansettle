-- Table occupancy — a distinct concept from dining_table.status (which means "is
-- this table enabled in the system at all"). FREE/OCCUPIED tracks whether a party
-- is currently seated with an open bill, so the QR scan can stop serving a paid
-- bill's numbers once it's settled — matching how a real POS records table state.
alter table dining_table add column occupancy_status varchar(16) not null default 'FREE';
