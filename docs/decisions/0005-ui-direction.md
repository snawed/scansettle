# ADR-0005: UI direction — Option C, Merchant Utility

Status: Accepted
Date: 2026-08-29

## Context

Phase 1 produced three visual directions (Fintech Minimal, Modern Premium, Merchant
Utility) across all 8 required screens: [ScanSettle UI Directions](https://claude.ai/code/artifact/75641718-c649-4cca-909d-55c71bdb56ab).

## Decision

Product Owner selected **Option C — Merchant Utility** as the production visual
direction for the whole product (merchant portal and customer-facing pay/Tables
pages).

## Design system carried forward

- Typography: IBM Plex Sans (UI text) + IBM Plex Mono (amounts, references, status
  codes).
- Colour: light neutral background (#F4F5F7 / panels #FFFFFF), dark sidebar
  (#161A23) for the merchant portal nav, functional accent blue (#0F62FE), and
  status colour-coding (paid/green #12805C on #E4F5EE, pending/amber #B5750A on
  #FDF1DD, failed/red #C22A2A on #FBE7E7).
- Density: compact rows, small radius (6px), sidebar-based navigation, data tables
  as the default list pattern in the merchant portal.
- Customer-facing screens (Pay by Bank, bank selection, success, Tables bill,
  split, tip) keep the same type/colour system but stay visually simple and
  uncluttered — the "utility" density applies to the merchant/staff-facing surfaces,
  not the anonymous customer payment journey, consistent with the brief's
  FAST/TRUSTWORTHY/SIMPLE/OBVIOUS principles for that journey (Section 18).

## Consequences

- Frontend component library (Phase 2 onward) is built against this design system —
  IBM Plex fonts, the colour tokens above, 6px radius, sidebar nav shell for the
  merchant portal.
- No further UI direction decisions are open; component-level design choices from
  here are implementation details, not direction choices.
