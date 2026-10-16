"""Keep the user-approved 1,000-item roadmap numbered and evidence-backed."""
import csv
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[1]
with (root / "REALISM_1000.csv").open(newline="", encoding="utf-8") as stream:
    rows = list(csv.DictReader(stream))
assert [int(row["id"]) for row in rows] == list(range(1, 1001)), "IDs must be exactly 1–1000, in order"
assert len({row["requirement"] for row in rows}) == 1000, "Do not replace requirements with duplicate filler"
assert len(Counter(row["workstream"] for row in rows)) == 20
assert set(Counter(row["workstream"] for row in rows).values()) == {50}
allowed = {"pending_audit", "partial", "implemented_in_source", "verified_testlab", "released"}
for row in rows:
    assert row["status"] in allowed, row
    assert row["requirement"].strip(), row
    if row["status"] != "pending_audit":
        assert row["source_evidence"], "Progress requires source evidence"
        for reference in row["source_evidence"].split(";"):
            assert (root / reference).is_file(), reference
    if row["status"] in {"implemented_in_source", "verified_testlab", "released"}:
        assert row["validation_evidence"], "Implementation requires named checks"
    if row["status"] != "released":
        assert row["remaining"].strip(), "Unreleased rows must identify the remaining work"
print("BLINK LIFE tracker: 1,000 unique requirements, stable IDs, 20 workstreams, valid evidence.")
print(dict(Counter(row["status"] for row in rows)))
