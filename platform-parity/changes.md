# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update this ledger when the change is not implemented entirely in shared code.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-09-07 | Windows desktop foundation | Existing production client | Desktop shell + EXE/MSI build foundation | Initial migration foundation; existing Android routes still need incremental porting |

## Allowed platform exception format

Use an exception only when a feature is truly platform-specific.

```text
Date:
Feature:
Android behavior:
Why it cannot/should not exist identically on Windows:
Windows equivalent:
Tests/validation:
```

A platform exception must never be used just to avoid implementing normal feature parity.
