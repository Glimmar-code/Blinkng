#!/usr/bin/env python3
"""Regression tests for public table Data API permission decisions."""

import unittest

from check_supabase_data_api_grants import check_sql


class DataApiGrantCheckTest(unittest.TestCase):
    def test_backend_only_table(self):
        sql = """
        CREATE TABLE IF NOT EXISTS public.payment_events (id bigint);
        ALTER TABLE public.payment_events ENABLE ROW LEVEL SECURITY;
        REVOKE ALL ON TABLE public.payment_events FROM anon, authenticated;
        GRANT SELECT, INSERT, UPDATE ON TABLE public.payment_events TO service_role;
        """
        self.assertEqual(check_sql(sql), [])

    def test_authenticated_read_only(self):
        sql = """
        CREATE TABLE public.campus_directory (id int);
        ALTER TABLE public.campus_directory ENABLE ROW LEVEL SECURITY;
        REVOKE ALL ON public.campus_directory FROM anon;
        GRANT SELECT ON public.campus_directory TO authenticated, service_role;
        """
        self.assertEqual(check_sql(sql), [])

    def test_new_table_without_security_fails(self):
        errors = check_sql('CREATE TABLE public.users_private (id int);')
        self.assertEqual(len(errors), 2)
        self.assertIn('ROW LEVEL SECURITY', errors[0])
        self.assertIn('anon, authenticated, service_role', errors[1])

    def test_partial_privileges_fail(self):
        sql = """
        CREATE TABLE public.foo(id int);
        ALTER TABLE public.foo ENABLE ROW LEVEL SECURITY;
        GRANT SELECT ON public.foo TO authenticated;
        """
        self.assertEqual(len(check_sql(sql)), 1)
        self.assertIn('anon, service_role', check_sql(sql)[0])

    def test_schema_wide_grant_does_not_replace_per_table_decision(self):
        sql = """
        CREATE TABLE public.foo(id int);
        ALTER TABLE public.foo ENABLE ROW LEVEL SECURITY;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
        """
        self.assertEqual(len(check_sql(sql)), 1)

    def test_private_schema_is_not_exposed(self):
        self.assertEqual(check_sql('CREATE TABLE private.secret (id int);'), [])

    def test_comments_and_literals_cannot_fake_permissions(self):
        sql = """
        CREATE TABLE public.foo (id int);
        ALTER TABLE public.foo ENABLE ROW LEVEL SECURITY;
        -- GRANT ALL ON public.foo TO anon, authenticated, service_role;
        SELECT 'GRANT ALL ON public.foo TO anon, authenticated, service_role';
        """
        self.assertEqual(len(check_sql(sql)), 1)

    def test_plpgsql_body_does_not_trigger_new_table_check(self):
        sql = """
        CREATE FUNCTION public.make_tables() RETURNS void AS $$
        BEGIN EXECUTE 'CREATE TABLE public.fake (id int)'; END;
        $$ LANGUAGE plpgsql;
        """
        self.assertEqual(check_sql(sql), [])

    def test_quoted_table_name(self):
        sql = """
        CREATE TABLE public."Campus Data"(id int);
        ALTER TABLE public."Campus Data" ENABLE ROW LEVEL SECURITY;
        REVOKE ALL ON TABLE public."Campus Data" FROM anon, authenticated;
        GRANT SELECT ON TABLE public."Campus Data" TO service_role;
        """
        self.assertEqual(check_sql(sql), [])

    def test_unqualified_table_checked_as_public(self):
        self.assertTrue(any(
            'public.subscription_events' in error
            for error in check_sql('CREATE TABLE subscription_events (id int);')
        ))


if __name__ == '__main__':
    unittest.main()
