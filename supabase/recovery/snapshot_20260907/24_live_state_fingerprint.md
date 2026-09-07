# Blink Supabase live-state fingerprint — 2026-09-07

Project: `Blink` (`jhwgifrlxwspoedxjaly`)

This manifest fingerprints the live backend without storing production user rows, Auth users, uploaded object bytes, tokens, passwords, service-role keys, SMTP credentials, OAuth secrets, Firebase private keys, or payment secrets.

The canonical ordering rules used to produce these hashes are implemented in `25_verify_live_fingerprint.sql`.

## Database object fingerprints

| Area | Count | MD5 fingerprint |
|---|---:|---|
| Tables (`private`, `private_ranking`, `public`) | 133 | `0c7bf1b2e58fe6ec3346334026a112d4` |
| Constraints | 556 | `055cf21cddf7dbce6763b29b0df3f662` |
| Indexes | 458 | `767ce81f7a8b79e7122bc358d7b618c4` |
| SQL functions/procedures | 319 | `1d2ef17819c3b33039f4332dd26e87d5` |
| Views/materialized views | 3 | `b4431e20dc3a047f53bf6caaa3acba62` |
| Custom triggers (`auth`, `private`, `private_ranking`, `public`) | 83 | `f544a2a36aee003c398d8fbe06c306ed` |
| RLS policy definitions (`public`, `storage`) | 310 | `466dc57fbccfa02e751d4ca2789f75cf` |
| RLS enable/force state (`private`, `private_ranking`, `public`, `storage`) | 141 | `982f3811309115667c9758d66b164f8e` |
| Table grants for `anon`, `authenticated`, `service_role` | 1466 | `aef76448ede722e873b9a1086c3f31a2` |
| Default ACL entries | 30 | `43f70f626d215c5678c3afe433148ce9` |

## Supabase service fingerprints

| Area | Count | MD5 fingerprint |
|---|---:|---|
| Storage buckets | 10 | `907d7379455b2b826d8f6678a1299da4` |
| `supabase_realtime` publication tables | 32 | `976c3b2c3835b47003bbffafd990420a` |
| pg_cron jobs | 9 | `e2c22bd30227b8dd4109acf9f50156ab` |

## Controlled/reference-data fingerprints

These are configuration/catalog rows that affect app/backend behavior. They are fingerprints only; production user/content data is intentionally excluded.

| Table | Rows | MD5 fingerprint |
|---|---:|---|
| `private_ranking.discovery_verification_multipliers` | 3 | `3cdcf89b2b871a28ee8a26c109d091b6` |
| `private.admin_feature_catalog` | 200 | `a076de417af98b9fdafa0eb10145a5ba` |
| `private.admin_feature_registry_v2` | 700 | `6b3f2ccdc0e2ba236863273d20980a31` |
| `private.admin_role_templates` | 7 | `9367d582a136d43e5b2e98dca94f96fd` |
| `private.admin_sections_v3` | 39 | `30d5281ff617634cbbf3ab9f17aefad2` |
| `private.admin_system_config` | 17 | `cd0735265982f7881d3a3e284e7929d8` |
| `private.admin_university_catalog` | 171 | `dcb1a42f5b174ac45465e12a7a1bad76` |
| `public.blink_store_catalog` | 50 | `f0a0d0c6ede016cac6316cd2f5ce755f` |
| `public.connect_category_catalog` | 20 | `f3c8edb60d152be67e216a67d76cca77` |
| `public.game_questions` | 30 | `e1b11244bb016844e05aff906cbce304` |

## Live Edge Function deployment manifest

Secret values are never stored here.

| Function | Live version | JWT verification | Import map | Live bundle SHA-256 |
|---|---:|---|---|---|
| `game-rewards` | 2 | enabled | no | `b0e5e87e31dcbc1b8db3276a72a3bcee4f4d24a6b4fb83a1412aa447dc6c831a` |
| `moderation-action` | 2 | enabled | no | `51cb23b7adc4943ef7316b5d6301e53b8d6338028aab3a9c0e20a22de5d21ec0` |
| `verify-payment` | 2 | enabled | no | `a69c8701134bda3888ebab79eea9c89d36eccb73e12498e9b28a8ae13a944596` |
| `send-push-notification` | 4 | enabled | yes | `9af1fb914ba10880ad4a54067b600fdca6b3464fa92356818c0f0c564cdb68b4` |
| `username-login` | 1 | disabled | no | `adbd32d51c6f8188adddbf94e7c1eb40a3baf30e666d541b341da34c044fc250` |
| `share-preview` | 1 | disabled | no | `0ff8dc4b17b0a899ba30b1e96872f244f6c76270ac268bf9d471437ded57e9cc` |
| `blink-web` | 1 | disabled | no | `2891e5636492f556b05da2b9e23020b0180e731c9fd65760afe17d8045eddf85` |
| `send-call-notification` | 2 | enabled | no | `f8598d130669efa94c38e8d6d4675e0b69f46634ea56f59c2e3c6f8f3bb98968` |

Repository source for these functions lives under `supabase/functions/`. A recovery is not accepted until the deployed functions are checked against the repository source and the required secret values are restored from the approved secret manager.

## Interpretation

A count or hash change means the live Supabase backend changed. Under the Blink release policy, that change must be represented in GitHub, reviewed for Android impact, tested, and included in the next compatible release before the recovery fingerprint is intentionally updated.
