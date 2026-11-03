# Disposable Contest/Post/Chat service checks

`feature-local-supabase.yml` creates a fresh Supabase project with Auth,
PostgREST, Storage and Realtime, then runs `verify.mjs` with Node 24. It uses
CLI 2.122.0 and no hosted credentials. The project must be named
`blink-feature-isolated`, have zero Auth users and expose its API on
`127.0.0.1:54331`. The verifier refuses other endpoints/projects and never
accepts a production database URL.

The workflow configuration is the reproducible setup. On a sufficiently sized
local Docker host, create the same config in a new temporary directory, run
`supabase --workdir <directory> start` with the workflow's exclusions, then:

```sh
node supabase/tests/local-features/verify.mjs <directory>
supabase --workdir <directory> stop --no-backup
```

The verifier applies the exact tracked feature migrations to that disposable
database and exercises real password sessions, Contest RPCs and concurrent
votes, recipient-only notifications over Realtime/REST, public-preview Storage
upload/download/delete ACLs, native Chat poll permissions/idempotency/blocking,
and posting expiry/identity/private-audience guards. It found the missing
owner SELECT policy that made Storage delete return success without deleting.
The deletion assertion is retained after the policy repair.

The prerequisite application schema and admin membership helper are synthetic.
This does **not** establish compatibility with every deployed table/trigger,
production admin configuration, authorized message transport, complete post
publication, or interaction between two running clients. Those remain separate
acceptance checks. Local public HTTP previews are tested separately from the
Contest RPC's required HTTPS media URL contract.

The full database image exceeded this cloud container's temporary disk capacity
under its Docker storage driver. The actual service suite subsequently passed
on GitHub's disposable runner (run 40958927594, commit `a4efb1dc`). No local
startup failure is recorded as a pass. Startup logs/keys stay private; CI failure
annotations redact local tokens and connection strings.
