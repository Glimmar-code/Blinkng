-- Additive, backwards-compatible rule: new Contest applications require media.
-- Existing entries may be text-only: preserve them for review and payout history.
-- Direct client table writes are still denied; only guarded RPCs create entries.
begin;

alter table public.blink_contest_entries
  add column legacy_text_only boolean not null default false;

update public.blink_contest_entries
set legacy_text_only = true
where media_url is null or media_url !~ '^https://[^/[:space:]]+';

alter table public.blink_contest_entries
  add constraint blink_contest_entry_media_required
  check (
    legacy_text_only or
    (media_url is not null and media_url ~ '^https://[^/[:space:]]+')
  );

comment on column public.blink_contest_entries.legacy_text_only is
  'Backwards-compatibility exemption for applications created before media was required. New entries always default false.';

notify pgrst, 'reload schema';
commit;
