# BLINK — essential email phase 1 (Testlab)

Status: **template/config groundwork only — not enabled in hosted Supabase or live email sending**.

## Scope

Ship essential account email first, using **Supabase Auth** for all account-authentication and security events. Do not implement a parallel homegrown OTP or password-reset token system.

| Event | Supabase category | Source template |
| --- | --- | --- |
| Sign-up email verification | Confirmation | `supabase/templates/confirmation.html` |
| Password reset requested | Recovery | `supabase/templates/recovery.html` |
| Confirm new email address | Email change | `supabase/templates/email_change.html` |
| Password changed | Security notification | `supabase/templates/password_changed_notification.html` |
| Email changed | Security notification | `supabase/templates/email_changed_notification.html` |

These five local templates are configured in `supabase/config.toml`. Supabase-managed token validation and existing BLINK recovery/deep-link handling must remain authoritative.

**Not enabled in this phase:** follower/like emails, promotional or bulk broadcasts, general message emails, game invites, new-device login alerts, or a welcome-email blast. A new-device alert requires trustworthy server-side login/audit events and deduplication; do not claim it exists based on client-side login callbacks. Messages should be a later opt-in digest, not one email per message.

## Brevo/Supabase account configuration (required outside Git)

1. Create or activate a Brevo account using the free transactional sending plan.
2. Add and authenticate an owned sending domain in Brevo (including provider-provided SPF/DKIM/DMARC DNS configuration); create a verified sender such as `no-reply@<owned-domain>`. An unpurchased domain is **not** a production sender.
3. In Brevo SMTP & API settings obtain the SMTP **login** and **SMTP key** (not an API key). Keep them outside source control and chats.
4. In the *staging Supabase project*, navigate to **Authentication → Emails → SMTP Settings**, enable custom SMTP, set sender name **BLINK**, verified sender email and:
   - Host: `smtp-relay.brevo.com`
   - Port: `587` with STARTTLS, or `465` with TLS
   - Username: the actual **SMTP login shown by Brevo**
   - Password: the **SMTP key**
5. In **Authentication → Email Templates**, copy the tested HTML into the matching templates for confirmation, recovery, email change, password changed, and email changed. Enable the two security notification types explicitly. This is required for **hosted** Supabase: editing local `config.toml` alone **does not** update hosted settings.
6. Keep confirm-signup and secure email-change verification enabled. Review and strictly allowlist Supabase Auth redirect URLs and BLINK Android/Windows/web recovery callbacks; validate both Android and desktop recovery before release.
7. Confirm SPF/DKIM alignment, a valid DMARC policy, and a monitored support path before public rollout. Do not embed secret keys in Android, desktop, or web.
8. Set a sensible Supabase Auth email rate limit and quota alert. Brevo's free plan currently advertises **300 emails/day**, shared across notification categories. Reserve capacity for verification and recovery ahead of optional emails.
9. Repeat only after Testlab and staging results are recorded. Production configuration must be performed separately, not by copying staging secrets.

## Acceptance checks — release blockers

- Verify sign-up email arrives only at a test address, links to the correct BLINK account, and no new session can bypass the intended confirmation policy.
- Verify password recovery from Android **and Windows/web**; valid links work once and expired/reused links fail. Existing reset screens must work before any rollout.
- Verify email-change confirmation, rejection of expired links, and notices to the intended addresses (including old-address notification when supported/configured).
- Verify password-changed and email-changed notices are actually triggered by Supabase Auth project security settings; local templates by themselves do not prove that.
- Verify user-facing error messages do not reveal whether a targeted email address is registered.
- Check email appearance in Gmail/Outlook and on narrow mobile viewports; accessible text and working call-to-action links.
- Verify failure cases: provider errors, mail throttling/quota exhaustion, bounce, spam filtering, and retries from *Supabase Auth* without duplicate application sends.
- Verify no email API/SMTP credentials leak into Git, APK, logs, web assets, or screenshots.
- Run Android unit/lint/build and Windows build/parity gates, plus Supabase staging auth/security smoke tests.
- Keep `main` untouched until the staging service and required release gates pass.

## Safety notes

- **Never send raw passwords, auth tokens, or private message content** through a new app-owned email dispatcher.
- The action links in these HTML files intentionally use Supabase's `{{ .ConfirmationURL }}` template variable; do not invent or log recovery URLs.
- Avoid tracking pixels and click tracking in account security emails.
- BLINK's existing push notifications remain separate.
- Provider/sender settings and Email Templates changes are managed in hosted Supabase dashboard; these are *not automatically deployed* from repository config.

## Official references

- Supabase Auth SMTP: https://supabase.com/docs/guides/auth/auth-smtp
- Supabase Email Templates: https://supabase.com/docs/guides/auth/auth-email-templates
- Supabase local email templates: https://supabase.com/docs/guides/local-development/customizing-email-templates
- Brevo + Supabase SMTP: https://developers.brevo.com/docs/supabase-smtp-integration
- Brevo pricing/limits: https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans
