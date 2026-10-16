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

TEMPLATES_TO_CHECK = {
    "recovery.html": 1,
    "email_change.html": 1,
    "password_changed_notification.html": 0,
    "email_changed_notification.html": 0,
    "welcome.html": 0,
    "new_device_alert.html": 0,
    "suspicious_activity_alert.html": 0,
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
