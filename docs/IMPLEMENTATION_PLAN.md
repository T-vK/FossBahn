# Implementation plan (session tracker)

Temporary tracker for the large UX/feature batch. Delete or archive when complete.

## Status legend
- [ ] todo
- [x] done
- [~] partial

## Items

| # | Feature | Status |
|---|---------|--------|
| 1 | Expandable journey card sections (legs, details) | [x] |
| 2 | Price tap → bahn.de booking (ctxRecon link) | [x] |
| 3 | Full-screen journey detail (search + live updates) | [x] |
| 4 | Live Updates: same JourneyCard UI as search | [x] |
| 5 | Live Updates: hide long-arrived connections | [x] |
| 6 | Recent locations cache (instant, before API) | [x] |
| 7 | Clear recent locations | [x] |
| 8 | Favorite routes screen + save/load | [x] |
| 9 | Favorite station markers (optional quick-pick) | [x] |
| 10 | Departure vs arrival for date/time (search UI) | [x] |
| 11 | Single date/time field + dialog + "Now" | [x] |
| 12 | First-launch Deutschland-Ticket prompt | [x] |
| 13 | Favorites tab in bottom nav | [x] |
| 14 | CI green on main | [~] |

## Notes
- Push directly to `main`, commits: `feat:` / `fix:` only.
- Room DB version bump (destructive migration OK).
