"""BLINK new-member staging gate: static checks that cannot send messages."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
def source(name):
    return (ROOT / name).read_text(encoding="utf-8")

class NewMemberTests(unittest.TestCase):
    def test_welcome_is_confirmed_private_and_deduplicated(self):
        sql = source("supabase/migrations/20261016093000_blink_essential_email_outbox.sql").lower()
        for s in ("email_confirmed_at is not null", "on conflict (user_id, kind, event_key) do nothing", "enable row level security", "to service_role"):
            self.assertIn(s, sql)
        self.assertNotIn("enable_confirmations", sql)

    def test_welcome_worker_is_internal(self):
        code = source("supabase/functions/send-essential-email/index.ts")
        for s in ('req.method !== "POST"', "x-blink-email-worker-secret", "BREVO_API_KEY", "BLINK_EMAIL_SENDER_ADDRESS", "api.brevo.com/v3/smtp/email", "const socialEnabled = false"):
            self.assertIn(s, code)
        self.assertIn("support@blink.com.ng", code)

    def test_worker_schedule_requires_explicit_enablement(self):
        job = source(".github/workflows/welcome-email-dispatch.yml")
        for part in (
            "BLINK_EMAIL_DELIVERY_ENABLED",
            "BLINK_EMAIL_WORKER_SECRET",
            "BLINK_SUPABASE_URL",
            "x-blink-email-worker-secret",
            "contents: read",
        ):
            self.assertIn(part, job)
        self.assertNotIn("service_role", job)

    def test_edge_function_auth_modes_are_pinned(self):
        config = source("supabase/config.toml")
        self.assertIn("[functions.send-essential-email]\nverify_jwt = false", config)
        self.assertIn("[functions.blink-contact-discovery]\nverify_jwt = true", config)

    def test_contacts_are_opt_in_and_verification_gated(self):
        migration = source("supabase/migrations/20261027002000_private_contact_discovery.sql").lower()
        edge = source("supabase/functions/blink-contact-discovery/index.ts")
        app = source("app/src/main/java/com/example/data/repository/BlinkContactsDiscoveryClient.kt")
        card = source("app/src/main/java/com/example/ui/components/BlinkContactDiscoveryCard.kt")
        for s in ("blink_contact_settings", "blink_contact_imports", "enable row level security", "to service_role"):
            self.assertIn(s, migration)
        for s in ("BLINK_CONTACT_HMAC_SECRET", "auth.getUser(jwt)", "blink_contact_upsert_opted_in"):
            self.assertIn(s, edge)
        self.assertIn("READ_CONTACTS", app)
        self.assertIn("numbers.isEmpty()", app)
        self.assertIn("RequestPermission()", card)
        self.assertIn("if (!it.syncEnabled) {", card)
        self.assertIn("people = emptyList()", card)
        self.assertIn("}.onFailure { problem =", card)
        self.assertNotIn("READ_CALL_LOG", app)

    def test_no_sms_android_consent_still_requires_real_backend(self):
        onboarding = source("app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt")
        contacts = source("app/src/main/java/com/example/ui/components/BlinkContactDiscoveryCard.kt")
        prompt = source("app/src/main/java/com/example/ui/components/BlinkContactSyncLoginPrompt.kt")
        service = source("app/src/main/java/com/example/data/supabase/SupabaseService.kt")
        for ui in (onboarding, contacts):
            self.assertNotIn("Send SMS code", ui)
            self.assertNotIn("BlinkPhoneVerificationClient", ui)
        self.assertIn("Phone number (optional)", onboarding)
        self.assertIn("onSavePhone(number)", onboarding)
        self.assertIn("phone_visibility", service)
        self.assertIn('"PRIVATE"', service)
        self.assertIn("ActivityResultContracts.RequestPermission()", prompt)
        self.assertIn("val snapshot = runCatching { client.get() }", prompt)
        self.assertIn("return@LaunchedEffect", prompt)

    def test_sync_is_opt_in_without_sms_and_signup_is_not_blocked(self):
        onboarding = source("app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt")
        client = source("app/src/main/java/com/example/data/repository/BlinkContactsDiscoveryClient.kt")
        app = source("app/src/main/java/com/example/MainActivity.kt")
        self.assertIn("No SMS verification", onboarding)
        self.assertIn("BlinkPhonePolicy.normalize(phoneNumber)", onboarding)
        self.assertIn("phoneNumber.isBlank()", onboarding)
        self.assertIn("BlinkContactSyncLoginPrompt(", app)
        self.assertIn("READ_CONTACTS", client)
        self.assertIn("Consent(", client)
        self.assertNotIn("READ_CALL_LOG", client)

if __name__ == "__main__":
    unittest.main()
