"""Static safety checks for BLINK essential transactional email (staging-only).

Run: python3 -m unittest discover -s supabase/tests -p 'test_essential_email_templates.py'
No provider calls, credentials, real accounts or mail sends occur here.
"""

import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEMPLATES = ROOT / "supabase" / "templates"
CONFIG = ROOT / "supabase" / "config.toml"
MIGRATION = ROOT / "supabase" / "migrations" / "20261016093000_blink_essential_email_outbox.sql"
WORKER = ROOT / "supabase" / "functions" / "send-essential-email" / "index.ts"
SOCIAL_MIGRATION = ROOT / "supabase" / "migrations" / "20261016120000_blink_opt_in_social_email.sql"
UNSUBSCRIBE = ROOT / "supabase" / "functions" / "unsubscribe-social-email" / "index.ts"
ANDROID = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "notification" / "NotificationAndCallSettingsActivity.kt"
WINDOWS = ROOT / "desktopApp" / "src" / "main" / "kotlin" / "com" / "blinkng" / "desktop" / "ui" / "ContentScreens.kt"
SHARED = ROOT / "shared" / "src" / "main" / "kotlin" / "com" / "blinkng" / "shared" / "notification" / "BlinkSocialEmailCategory.kt"

TEMPLATES_TO_CHECK = {
    "recovery.html": 1,
    "email_change.html": 1,
    "password_changed_notification.html": 0,
    "email_changed_notification.html": 0,
    "welcome.html": 0,
    "new_device_alert.html": 0,
    "suspicious_activity_alert.html": 0,
    # Opt-in social notification designs only; no social emails are enabled.
    "new_follower.html": 0,
    "unread_messages_digest.html": 0,
    "post_engagement_digest.html": 0,
    "reel_milestone.html": 0,
    "leaderboard_digest.html": 0,
}


class EssentialEmailSafetyTests(unittest.TestCase):
    def test_all_templates_present_valid_size_and_branded(self):
        for name in TEMPLATES_TO_CHECK:
            with self.subTest(template=name):
                html = (TEMPLATES / name).read_text(encoding="utf-8")
                self.assertLessEqual(len(html), 50_000)
                self.assertTrue(html.startswith("<!doctype html>"))
                self.assertIn("<title>BLINK", html)
                self.assertIn("background-color:#000000", html)
                self.assertIn("#ffffff", html.lower())
                self.assertIn("blinksupport.ng@gmail.com", html)
                self.assertIn("</html>", html)
                for element in ("html", "head", "body", "table", "tr", "td", "p"):
                    opens = len(re.findall(r"<" + element + r"(?=[\s>])", html, flags=re.I))
                    closes = len(re.findall(r"</" + element + r">", html, flags=re.I))
                    self.assertEqual(opens, closes, f"{name}: unbalanced {element}")
                self.assertNotRegex(html.lower(), r"<script\b|<img\b|javascript:|http://localhost")
                self.assertNotIn("access_token=", html)
                self.assertNotIn("refresh_token=", html)

    def test_only_supabase_auth_templates_embed_confirmation_url(self):
        for name, required_count in TEMPLATES_TO_CHECK.items():
            with self.subTest(template=name):
                html = (TEMPLATES / name).read_text(encoding="utf-8")
                self.assertEqual(html.count("{{ .ConfirmationURL }}"), required_count)

    def test_social_templates_have_no_private_message_or_session_data(self):
        names = (
            "new_follower.html", "unread_messages_digest.html",
            "post_engagement_digest.html", "reel_milestone.html",
            "leaderboard_digest.html",
        )
        for name in names:
            with self.subTest(template=name):
                html = (TEMPLATES / name).read_text(encoding="utf-8").lower()
                self.assertIn("opt in", html)
                self.assertNotIn("{{ .confirmationurl }}", html)
                self.assertNotIn("access_token=", html)
                self.assertNotIn("refresh_token=", html)
                self.assertNotIn("http://localhost", html)

    def test_new_social_email_preferences_default_off_and_private(self):
        sql = SOCIAL_MIGRATION.read_text(encoding="utf-8").lower()
        # A broken PL/pgSQL dollar quote would make the staging migration fail.
        self.assertEqual(sql.count("$$"), 4)
        self.assertIn("end;\n$$;\nrevoke all on function public.blink_queue_social_email_digests", sql)

        for col in (
            "new_follower_enabled", "unread_messages_enabled",
            "post_engagement_enabled", "reel_milestone_enabled", "leaderboard_enabled",
        ):
            self.assertIn(col + " boolean not null default false", sql)
        self.assertIn("enable row level security", sql)
        self.assertIn("using ((select auth.uid()) = user_id)", sql)
        self.assertIn("with check ((select auth.uid()) = user_id)", sql)
        self.assertIn("revoke all on public.blink_social_email_progress from public, anon, authenticated", sql)
        self.assertIn("grant execute on function public.blink_queue_social_email_digests(integer) to service_role", sql)
        self.assertIn("new_follower_since", sql)
        self.assertIn("last_user_id", sql)
        self.assertIn("v_current_rank <> v_rank", sql)
        self.assertIn("distinct pv.viewer_id", sql)
        self.assertIn("cp.last_read_at", sql)
        self.assertNotIn("enable_confirmations", sql)

    def test_social_categories_present_on_android_windows_and_shared(self):
        shared = SHARED.read_text(encoding="utf-8")
        android = ANDROID.read_text(encoding="utf-8")
        windows = WINDOWS.read_text(encoding="utf-8")
        self.assertIn("BlinkSocialEmailCategory", shared)
        self.assertIn("BlinkSocialEmailPreferencesPanel()", android)
        self.assertIn("DesktopSocialEmailPreferencesPanel(state)", windows)
        for category in (
            "NEW_FOLLOWER", "UNREAD_MESSAGES", "POST_ENGAGEMENT",
            "REEL_MILESTONE", "LEADERBOARD",
        ):
            self.assertIn(category, shared)

    def test_social_worker_checks_consent_and_has_unsubscribe(self):
        worker = WORKER.read_text(encoding="utf-8")
        fn = UNSUBSCRIBE.read_text(encoding="utf-8")
        for kind in ("new_follower", "unread_messages", "post_engagement", "reel_milestone", "leaderboard"):
            self.assertIn(kind + ":", worker)
        self.assertIn("BLINK_SOCIAL_EMAIL_ENABLED", worker)
        self.assertIn("user_opted_out", worker)
        self.assertIn("email_notifs_enabled", worker)
        self.assertIn("socialUnsubscribeUrl", worker)
        self.assertIn("List-Unsubscribe", worker)
        self.assertIn("BLINK_EMAIL_UNSUBSCRIBE_SECRET", fn)
        self.assertIn('req.method === "GET"', fn)
        self.assertIn('req.method === "POST"', fn)
        self.assertIn('await crypto.subtle.verify("HMAC"', fn)
        self.assertIn('.update({ [FIELDS[valid.kind]]: false })', fn)
        self.assertNotIn("ConfirmationURL", fn)

    def test_signup_confirmation_is_not_added_or_enabled(self):
        config = CONFIG.read_text(encoding="utf-8")
        self.assertNotIn("[auth.email.template.confirmation]", config)
        self.assertNotIn("auth.email.enable_confirmations", config)
        self.assertFalse((TEMPLATES / "confirmation.html").exists())
        self.assertIn("[auth.email.template.recovery]", config)
        self.assertIn("[auth.email.template.email_change]", config)

    def test_worker_is_service_only_and_no_secret_is_in_client(self):
        source = WORKER.read_text(encoding="utf-8")
        config = CONFIG.read_text(encoding="utf-8")
        self.assertIn("[functions.send-essential-email]", config)
        self.assertIn("verify_jwt = false", config.split("[functions.send-essential-email]")[1].split("[")[0])
        self.assertIn("BLINK_EMAIL_WORKER_SECRET", source)
        self.assertIn("x-blink-email-worker-secret", source)
        self.assertIn("SUPABASE_SERVICE_ROLE_KEY", source)
        self.assertIn("BREVO_API_KEY", source)
        self.assertNotIn("{{ .ConfirmationURL }}", source)
        self.assertNotIn("SMTP_PASSWORD", source)
        self.assertIn("user.email_confirmed_at", source)
        self.assertIn("75", source)

    def test_outbox_denies_clients_and_welcome_requires_confirmation_state(self):
        sql = MIGRATION.read_text(encoding="utf-8").lower()
        self.assertIn("enable row level security", sql)
        self.assertIn("revoke all on public.blink_essential_email_outbox from public, anon, authenticated", sql)
        self.assertIn("to service_role", sql)
        self.assertIn("new.email_confirmed_at is not null", sql)
        self.assertIn("on conflict (user_id, kind, event_key) do nothing", sql)
        self.assertIn("after insert or update of email_confirmed_at on auth.users", sql)
        self.assertNotIn("mailer_autoconfirm", sql)
        self.assertNotIn("auth.email.enable_confirmations", sql)


if __name__ == "__main__":
    unittest.main()
