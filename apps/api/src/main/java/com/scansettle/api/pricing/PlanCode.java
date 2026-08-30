package com.scansettle.api.pricing;

/** docs/architecture.md Section 11 — BASIC ships in MVP; the rest are modelled now
 *  so pricing evolution doesn't require a schema change later. */
public enum PlanCode {
    BASIC,
    FREE,
    PRO,
    HOSPITALITY,
    ENTERPRISE
}
