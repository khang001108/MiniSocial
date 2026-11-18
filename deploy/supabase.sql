-- MiniSocial MVP schema for a dedicated Supabase project (PostgreSQL).
-- Run ONCE in Supabase SQL Editor. No secret keys belong in this SQL or Git.
-- Only backend with service_role may query these tables; direct mobile access is denied.

create table if not exists public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 display_name text not null check (char_length(display_name) between 1 and 50),
 handle text not null unique check (handle ~ '^[a-z0-9_]{3,24}$'),
 bio text not null default '' check (char_length(bio) <= 240),
 created_at timestamptz not null default now()
);

create or replace function public.minisocial_create_profile() returns trigger
language plpgsql security definer set search_path = public as $$
declare new_handle text;
begin
 new_handle := lower(coalesce(new.raw_user_meta_data ->> 'handle',''));
 if new_handle !~ '^[a-z0-9_]{3,24}$' then raise exception 'Invalid MiniSocial handle'; end if;
 insert into public.profiles (id,display_name,handle)
 values (new.id, left(coalesce(nullif(new.raw_user_meta_data ->> 'display_name',''),'Nokia user'),50),new_handle);
 return new;
end;
$$;

drop trigger if exists minisocial_profile_on_signup on auth.users;
create trigger minisocial_profile_on_signup after insert on auth.users
for each row execute function public.minisocial_create_profile();

create table if not exists public.media_files (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references public.profiles(id) on delete cascade,
 receiver_id uuid references public.profiles(id) on delete cascade,
 scope text not null check (scope in ('post','chat')),
 storage_path text not null unique,
 mime_type text not null check (mime_type in ('image/jpeg','image/png')),
 created_at timestamptz not null default now(),
 constraint media_recipient check ((scope = 'chat' and receiver_id is not null) or (scope = 'post' and receiver_id is null))
);

create table if not exists public.posts (
 id uuid primary key default gen_random_uuid(),
 author_id uuid not null references public.profiles(id) on delete cascade,
 body text not null default '' check (char_length(body) <= 500),
 media_id uuid references public.media_files(id) on delete set null,
 created_at timestamptz not null default now()
);

create table if not exists public.post_likes (
 post_id uuid not null references public.posts(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,
 primary key (post_id,user_id)
);

create table if not exists public.comments (
 id uuid primary key default gen_random_uuid(),
 post_id uuid not null references public.posts(id) on delete cascade,
 author_id uuid not null references public.profiles(id) on delete cascade,
 body text not null check (char_length(body) between 1 and 300),
 created_at timestamptz not null default now()
);

create table if not exists public.friendships (
 requester_id uuid not null references public.profiles(id) on delete cascade,
 addressee_id uuid not null references public.profiles(id) on delete cascade,
 status text not null default 'pending' check (status in ('pending','accepted')),
 created_at timestamptz not null default now(),
 primary key (requester_id,addressee_id),
 constraint not_self check (requester_id <> addressee_id)
);

create table if not exists public.messages (
 id uuid primary key default gen_random_uuid(),
 sender_id uuid not null references public.profiles(id) on delete cascade,
 recipient_id uuid not null references public.profiles(id) on delete cascade,
 body text not null default '' check (char_length(body) <= 500),
 media_id uuid references public.media_files(id) on delete set null,
 created_at timestamptz not null default now(),
 constraint not_self_msg check (sender_id <> recipient_id)
);

create index if not exists minisocial_posts_time on public.posts (created_at desc);
create index if not exists minisocial_posts_author_time on public.posts (author_id, created_at desc);
create index if not exists minisocial_comments_post on public.comments (post_id, created_at);
create index if not exists minisocial_messages_pair on public.messages (sender_id,recipient_id,created_at desc);
create index if not exists minisocial_messages_recipient on public.messages (recipient_id,created_at desc);
create index if not exists minisocial_requests_to on public.friendships (addressee_id,status);

create or replace view public.feed_items as
select p.id,p.author_id,pr.display_name,p.body,p.created_at,p.media_id,
 (select count(*) from public.post_likes l where l.post_id=p.id) as likes_count,
 (select count(*) from public.comments c where c.post_id=p.id) as comments_count
from public.posts p join public.profiles pr on pr.id=p.author_id;

create or replace view public.comment_items as
select c.id,c.post_id,c.author_id,p.display_name,c.body,c.created_at
from public.comments c join public.profiles p on p.id=c.author_id;

create or replace view public.friend_list as
select f.requester_id as user_id,f.addressee_id as friend_id,p.display_name,p.handle
from public.friendships f join public.profiles p on p.id=f.addressee_id
where f.status='accepted'
union all
select f.addressee_id as user_id,f.requester_id as friend_id,p.display_name,p.handle
from public.friendships f join public.profiles p on p.id=f.requester_id
where f.status='accepted';

create or replace view public.incoming_requests as
select f.addressee_id,f.requester_id,p.display_name,p.handle
from public.friendships f join public.profiles p on p.id=f.requester_id
where f.status='pending';

-- CRITICAL: service-role-only access. J2ME client must NEVER receive the service_role key.
alter table public.profiles enable row level security;
alter table public.media_files enable row level security;
alter table public.posts enable row level security;
alter table public.post_likes enable row level security;
alter table public.comments enable row level security;
alter table public.friendships enable row level security;
alter table public.messages enable row level security;
-- No anon/authenticated policies intentionally: every request goes through the authorized backend.
revoke all on public.profiles,public.media_files,public.posts,public.post_likes,public.comments,public.friendships,public.messages from anon,authenticated;
revoke all on public.feed_items,public.comment_items,public.friend_list,public.incoming_requests from anon,authenticated;
grant select,insert,update,delete on public.profiles,public.media_files,public.posts,public.post_likes,public.comments,public.friendships,public.messages to service_role;
grant select on public.feed_items,public.comment_items,public.friend_list,public.incoming_requests to service_role;

-- Storage bucket is private. Images are downloaded only after /m/photo checks authorization.
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('minisocial-private','minisocial-private',false,184320,array['image/jpeg','image/png']::text[])
on conflict (id) do update set public=false,file_size_limit=184320,allowed_mime_types=array['image/jpeg','image/png']::text[];
