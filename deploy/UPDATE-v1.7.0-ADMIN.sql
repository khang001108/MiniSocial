-- MiniSocial v1.7.0: non-destructive admin, username login and quota migration.
-- IMPORTANT: Run UPDATE-v1.6.0.sql first if it has not been applied.
-- Run only in the dedicated MiniSocial Supabase project, never Gunny.
-- Back up profiles/auth and storage before running. No user data deleted.
BEGIN;

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS auth_email text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_suspended boolean NOT NULL DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS storage_limit_bytes bigint NOT NULL DEFAULT 52428800;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS storage_used_bytes bigint NOT NULL DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS sessions_revoked_before bigint NOT NULL DEFAULT 0;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS ms_profile_storage_nonnegative;
ALTER TABLE public.profiles ADD CONSTRAINT ms_profile_storage_nonnegative CHECK (storage_limit_bytes >= 0 AND storage_used_bytes >= 0);

-- Backfill emails for existing Auth accounts; new users get an INTERNAL virtual login
-- identifier. Users never enter or receive an email for these virtual accounts.
UPDATE public.profiles p SET auth_email = lower(u.email)
FROM auth.users u WHERE p.id=u.id AND (p.auth_email IS NULL OR p.auth_email='');
CREATE UNIQUE INDEX IF NOT EXISTS ms_profiles_auth_email_unique ON public.profiles(auth_email) WHERE auth_email IS NOT NULL;

ALTER TABLE public.media_files ADD COLUMN IF NOT EXISTS size_bytes bigint NOT NULL DEFAULT 0;
ALTER TABLE public.media_files DROP CONSTRAINT IF EXISTS ms_media_size_nonnegative;
ALTER TABLE public.media_files ADD CONSTRAINT ms_media_size_nonnegative CHECK (size_bytes >= 0 AND size_bytes <= 184320);

-- Best-effort size backfill for already uploaded objects. Storage metadata is
-- not always populated; missing sizes remain zero until manually reconciled.
UPDATE public.media_files m SET size_bytes = LEAST(184320,(o.metadata->>'size')::bigint)
FROM storage.objects o
WHERE o.bucket_id='minisocial-private' AND o.name=m.storage_path
  AND m.size_bytes=0 AND coalesce(o.metadata->>'size','') ~ '^[0-9]{1,12}$';

CREATE TABLE IF NOT EXISTS public.minisocial_limits (
 id integer PRIMARY KEY CHECK (id=1),
 total_limit_bytes bigint NOT NULL DEFAULT 536870912 CHECK (total_limit_bytes >= 1048576),
 used_bytes bigint NOT NULL DEFAULT 0 CHECK (used_bytes >= 0),
 registration_enabled boolean NOT NULL DEFAULT true,
 updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO public.minisocial_limits(id) VALUES(1) ON CONFLICT DO NOTHING;

-- Regenerate counts of stored content. Counts payload text and uploaded byte sizes;
-- excludes Postgres indexes/WAL/row headers and Storage provider overhead.
WITH per_user AS (
 SELECT uid,sum(sz)::bigint AS n FROM (
  SELECT owner_id AS uid,size_bytes AS sz FROM public.media_files
  UNION ALL SELECT author_id,octet_length(body)::bigint FROM public.posts
  UNION ALL SELECT author_id,octet_length(body)::bigint FROM public.comments
  UNION ALL SELECT sender_id,octet_length(body)::bigint FROM public.messages
 ) content GROUP BY uid
)
UPDATE public.profiles p SET storage_used_bytes=coalesce(u.n,0)
FROM (SELECT p2.id,coalesce(u.n,0) AS n FROM public.profiles p2 LEFT JOIN per_user u ON u.uid=p2.id) u
WHERE p.id=u.id;
UPDATE public.minisocial_limits SET used_bytes=(SELECT coalesce(sum(storage_used_bytes),0) FROM public.profiles),updated_at=now() WHERE id=1;

-- Trigger existing on auth.users must remain, as the new username-only flow
-- creates users via Supabase Auth admin API with email_confirm=true.
CREATE OR REPLACE FUNCTION public.minisocial_create_profile() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE new_handle text;
BEGIN
 new_handle := lower(coalesce(new.raw_user_meta_data->>'handle',''));
 IF new_handle !~ '^[a-z0-9_]{3,24}$' THEN RAISE EXCEPTION 'Invalid MiniSocial handle'; END IF;
 INSERT INTO public.profiles(id,display_name,handle,onboarding_complete,auth_email)
 VALUES (new.id,new_handle,new_handle,false,lower(new.email));
 RETURN new;
END;
$$;

-- Atomic quota accounting for every text insert and media upload. Global row is
-- locked before per-user row, avoiding concurrent over-allocations.
CREATE OR REPLACE FUNCTION public.minisocial_quota_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
 owner uuid;
 new_owner uuid;
 delta bigint:=0;
 old_size bigint:=0;
 new_size bigint:=0;
 global_used bigint;
 global_limit bigint;
 user_used bigint;
 user_limit bigint;
BEGIN
 IF TG_OP <> 'INSERT' THEN
  IF TG_TABLE_NAME='media_files' THEN owner:=OLD.owner_id;old_size:=OLD.size_bytes;
  ELSIF TG_TABLE_NAME='messages' THEN owner:=OLD.sender_id;old_size:=octet_length(OLD.body);
  ELSE owner:=OLD.author_id;old_size:=octet_length(OLD.body);
  END IF;
 END IF;
 IF TG_OP <> 'DELETE' THEN
  IF TG_TABLE_NAME='media_files' THEN new_owner:=NEW.owner_id;new_size:=NEW.size_bytes;
  ELSIF TG_TABLE_NAME='messages' THEN new_owner:=NEW.sender_id;new_size:=octet_length(NEW.body);
  ELSE new_owner:=NEW.author_id;new_size:=octet_length(NEW.body);
  END IF;
 END IF;
 IF TG_OP='INSERT' THEN owner:=new_owner;delta:=new_size;
 ELSIF TG_OP='DELETE' THEN delta:=-old_size;
 ELSE
  IF owner IS DISTINCT FROM new_owner THEN RAISE EXCEPTION 'Storage owner cannot change'; END IF;
  delta:=new_size-old_size;
 END IF;
 IF delta=0 THEN
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
 END IF;
 SELECT used_bytes,total_limit_bytes INTO global_used,global_limit
 FROM public.minisocial_limits WHERE id=1 FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Missing minisocial_limits migration'; END IF;
 IF global_used+delta > global_limit THEN RAISE EXCEPTION 'MiniSocial global storage limit exceeded' USING ERRCODE='23514'; END IF;
 SELECT storage_used_bytes,storage_limit_bytes INTO user_used,user_limit
 FROM public.profiles WHERE id=owner FOR UPDATE;
 IF FOUND THEN
  IF user_used+delta > user_limit THEN RAISE EXCEPTION 'Account storage limit exceeded' USING ERRCODE='23514'; END IF;
  UPDATE public.profiles SET storage_used_bytes=greatest(0,user_used+delta) WHERE id=owner;
 ELSIF TG_OP<>'DELETE' THEN
  RAISE EXCEPTION 'Storage owner not found';
 END IF;
 UPDATE public.minisocial_limits
 SET used_bytes=greatest(0,global_used+delta),updated_at=now() WHERE id=1;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ms_quota_media ON public.media_files;
DROP TRIGGER IF EXISTS ms_quota_post ON public.posts;
DROP TRIGGER IF EXISTS ms_quota_comment ON public.comments;
DROP TRIGGER IF EXISTS ms_quota_message ON public.messages;
CREATE TRIGGER ms_quota_media BEFORE INSERT OR UPDATE OR DELETE ON public.media_files FOR EACH ROW EXECUTE FUNCTION public.minisocial_quota_guard();
CREATE TRIGGER ms_quota_post BEFORE INSERT OR UPDATE OR DELETE ON public.posts FOR EACH ROW EXECUTE FUNCTION public.minisocial_quota_guard();
CREATE TRIGGER ms_quota_comment BEFORE INSERT OR UPDATE OR DELETE ON public.comments FOR EACH ROW EXECUTE FUNCTION public.minisocial_quota_guard();
CREATE TRIGGER ms_quota_message BEFORE INSERT OR UPDATE OR DELETE ON public.messages FOR EACH ROW EXECUTE FUNCTION public.minisocial_quota_guard();

ALTER TABLE public.minisocial_limits ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.minisocial_limits FROM anon,authenticated;
GRANT SELECT,UPDATE ON public.minisocial_limits TO service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.profiles,public.media_files,public.posts,public.comments,public.messages TO service_role;
COMMIT;
