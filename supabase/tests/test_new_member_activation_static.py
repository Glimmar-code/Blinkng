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

    def test_contacts_are_opt_in_and_verification_gated(self):
        migration = source("supabase/migrations/20261027002000_private_contact_discovery.sql").lower()
        edge = source("supabase/functions/blink-contact-discovery/index.ts")
        app = source("app/src/main/java/com/example/data/repository/BlinkContactsDiscoveryClient.kt")
        card = source("app/src/main/java/com/example/ui/components/BlinkContactDiscoveryCard.kt")
        for s in ("blink_contact_settings", "blink_contact_imports", "enable row level security", "to service_role"):
            self.assertIn(s, migration)
        for s in ("BLINK_CONTACT_HMAC_SECRET", "auth.getUser(jwt)", "phone_confirmed_at"):
            self.assertIn(s, edge)
        self.assertIn("READ_CONTACTS", app)
        self.assertIn("numbers.isEmpty()", app)
        self.assertIn("RequestPermission()", card)
        self.assertIn("if (!it.syncEnabled) people = emptyList()", card)
        self.assertNotIn("READ_CALL_LOG", app)

    def test_sms_does_not_block_new_accounts_before_provider_activation(self):
        policy = source("shared/src/main/kotlin/com/blinkng/shared/BlinkPhonePolicy.kt")
        client = source("app/src/main/java/com/example/auth/BlinkPhoneVerificationClient.kt")
        screen = source("app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt")
        self.assertIn("REQUIRE_VERIFIED_PHONE_IN_ONBOARDING: Boolean = false", policy)
        self.assertIn("phone_change", client)
        self.assertIn("SMS service is temporarily unavailable", client)
        self.assertIn("Entering your number does not send an SMS", screen)

if __name__ == "__main__":
    unittest.main()
