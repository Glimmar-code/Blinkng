#!/usr/bin/env python3
"""Real PostgreSQL concurrency/ACL test, confined to a new networkless container.

No database URLs, production credentials, host mounts or published ports are used.
Requires Docker and the pinned image; never falls back to an external database.
"""
import concurrent.futures
import os
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parent
IMAGE = "postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"
ENV = dict(os.environ)
for key in ("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"):
    ENV.pop(key, None)
DOCKER = ["docker", "--host=unix:///var/run/docker.sock"]
NAME = "blink-contest-test-" + uuid.uuid4().hex
ADMIN = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
CREATOR = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
VOTER = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
CONTEST = "11111111-1111-4111-8111-111111111111"
ENTRY = "22222222-2222-4222-8222-222222222222"


def docker(*args, **kwargs):
    return subprocess.run(DOCKER + list(args), env=ENV, text=True,
                          capture_output=True, timeout=90, **kwargs)


def sql(statement, *, success=True):
    result = docker("exec", "-i", NAME, "psql", "-U", "postgres", "-X", "-qAt",
                    "-v", "ON_ERROR_STOP=1", input=statement)
    if success and result.returncode:
        raise AssertionError(result.stderr)
    return result


def member(statement, user=VOTER, *, success=True):
    return sql(f"set role authenticated; set request.jwt.claim.sub = '{user}'; " + statement,
               success=success)


def denied(result, expected):
    assert result.returncode != 0 and expected in result.stderr, result.stderr


try:
    started = docker("run", "--detach", "--rm", "--name", NAME, "--network=none",
                     "--env", "POSTGRES_HOST_AUTH_METHOD=trust", IMAGE)
    assert started.returncode == 0, started.stderr
    for attempt in range(60):
        if docker("exec", NAME, "pg_isready", "-U", "postgres").returncode == 0:
            break
        time.sleep(1)
    else:
        raise AssertionError("Disposable PostgreSQL did not become ready")

    sql((ROOT / "contest-fixture.pgfixture").read_text())
    sql(f"insert into auth.users(id) values ('{ADMIN}'),('{CREATOR}'),('{VOTER}');")
    for migration in ("20261028185000_blink_contests_free_entry.sql",
                      "20261028200000_blink_contest_growth_v1.sql"):
        sql((ROOT.parent / "migrations" / migration).read_text())
    sql(f"""
        insert into public.blink_contests(id,title,created_by,status,
          registration_opens_at,registration_closes_at,voting_opens_at,voting_closes_at,results_at)
        values ('{CONTEST}','Isolated race fixture','{ADMIN}','published',
          now()-interval '3 days',now()-interval '2 days',now()-interval '1 day',
          now()+interval '1 day',now()+interval '2 days');
        insert into public.blink_contest_entries(id,contest_id,user_id,statement,status)
        values ('{ENTRY}','{CONTEST}','{CREATOR}','Synthetic entry for concurrency','approved');
    """)

    denied(sql("set role anon; select public.vote_blink_contest(null,null);", success=False), "permission denied")
    denied(member("select * from public.blink_contest_votes;", success=False), "permission denied")
    denied(member(f"update public.blink_contests set status='completed' where id='{CONTEST}';", success=False), "permission denied")
    denied(member(f"select public.admin_transition_blink_contest('{CONTEST}','pause');", success=False), "ADMIN_REQUIRED")
    denied(member(f"select public.vote_blink_contest('{CONTEST}','{ENTRY}');", CREATOR, success=False), "SELF_VOTE_NOT_ALLOWED")

    # Eight independent connections contend on the same row/unique constraint.
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        votes = list(pool.map(lambda _: member(
            f"select public.vote_blink_contest('{CONTEST}','{ENTRY}');", success=False), range(8)))
    assert sum(result.returncode == 0 for result in votes) == 1, [r.stderr for r in votes]
    for result in votes:
        if result.returncode:
            denied(result, "duplicate key value violates unique constraint")
    assert sql("select count(*) from public.blink_contest_votes;").stdout.strip() == "1"
    assert sql("select count(*) from public.notifications;").stdout.strip() == "1"

    member(f"select public.admin_transition_blink_contest('{CONTEST}','pause');", ADMIN)
    denied(member(f"select public.vote_blink_contest('{CONTEST}','{ENTRY}');", success=False), "VOTING_CLOSED")
    print("PASS isolated PostgreSQL: concurrent one-vote enforcement, atomic notification, anon/member/admin ACLs, self-vote and pause gates")
finally:
    # This exact random container is the only cleanup target, including failures.
    docker("rm", "--force", NAME)
