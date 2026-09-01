-- Keep public.profiles synchronized with Supabase Auth users.
-- Existing users are backfilled below; future signups are handled by the trigger.

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, email, full_name, username)
  VALUES (
    NEW.id,
    NEW.email,
    COALESCE(NULLIF(NEW.raw_user_meta_data->>'full_name',''), 'Blink User'),
    NULLIF(NEW.raw_user_meta_data->>'user_name','')
  )
  ON CONFLICT (id) DO UPDATE SET
    email = COALESCE(public.profiles.email, EXCLUDED.email),
    full_name = CASE
      WHEN public.profiles.full_name = 'Blink User' THEN EXCLUDED.full_name
      ELSE public.profiles.full_name
    END;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

INSERT INTO public.profiles (id, email, full_name)
SELECT
  u.id,
  u.email,
  COALESCE(NULLIF(u.raw_user_meta_data->>'full_name',''), 'Blink User')
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE p.id IS NULL
ON CONFLICT (id) DO NOTHING;
