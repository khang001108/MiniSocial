-- MiniSocial v1.6.0, run ONCE in the dedicated MiniSocial Supabase SQL Editor.
-- Non-destructive migration: existing profiles keep onboarding_complete=true.
-- Never run this on Gunny or a shared Auth project.
begin;

alter table public.profiles add column if not exists onboarding_complete boolean not null default true;
alter table public.profiles add column if not exists avatar_media_id uuid;

alter table public.media_files drop constraint if exists media_files_scope_check;
alter table public.media_files add constraint media_files_scope_check check (scope in ('post','chat','avatar'));
alter table public.media_files drop constraint if exists media_recipient;
alter table public.media_files add constraint media_recipient check (
  (scope='chat' and receiver_id is not null) or (scope in ('post','avatar') and receiver_id is null));
alter table public.media_files add column if not exists device_model text not null default '';
alter table public.posts add column if not exists device_model text not null default '';
alter table public.messages add column if not exists device_model text not null default '';
alter table public.comments add column if not exists device_model text not null default '';

alter table public.profiles drop constraint if exists minisocial_avatar_media_fk;
alter table public.profiles add constraint minisocial_avatar_media_fk foreign key (avatar_media_id)
 references public.media_files(id) on delete set null;

create or replace function public.minisocial_create_profile() returns trigger
language plpgsql security definer set search_path=public as $$
declare new_handle text;
begin
 new_handle := lower(coalesce(new.raw_user_meta_data ->> 'handle',''));
 if new_handle !~ '^[a-z0-9_]{3,24}$' then raise exception 'Invalid MiniSocial handle'; end if;
 insert into public.profiles(id,display_name,handle,onboarding_complete)
 values (new.id,new_handle,new_handle,false);
 return new;
end;
$$;

create or replace view public.feed_items as
select p.id,p.author_id,pr.display_name,p.body,p.created_at,p.media_id,
 (select count(*) from public.post_likes l where l.post_id=p.id) as likes_count,
 (select count(*) from public.comments c where c.post_id=p.id) as comments_count,
 p.device_model, pr.avatar_media_id
from public.posts p join public.profiles pr on pr.id=p.author_id;

create or replace view public.comment_items as
select c.id,c.post_id,c.author_id,p.display_name,c.body,c.created_at,c.device_model
from public.comments c join public.profiles p on p.id=c.author_id;

grant select on public.feed_items,public.comment_items to service_role;
commit;
