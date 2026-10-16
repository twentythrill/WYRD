-- ==============================================================================
-- WYRD: Complete Supabase Database Schema & Setup
-- Run this script in your Supabase SQL Editor (Dashboard -> SQL Editor -> New Query)
-- It is completely idempotent and safe to run on fresh or existing databases.
-- ==============================================================================

-- 1. Profiles Table (User settings, system prompt, models)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT DEFAULT '',
    system_prompt TEXT DEFAULT '',
    active_model TEXT DEFAULT 'mimo-v2.6-pro',
    reasoning_effort TEXT DEFAULT 'Medium',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_profiles_updated_at ON public.profiles(updated_at DESC);

-- 2. Projects Table (Isolated spaces with custom prompt overrides)
CREATE TABLE IF NOT EXISTS public.projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    system_prompt TEXT NOT NULL DEFAULT '',
    color TEXT NOT NULL DEFAULT '',
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON public.projects(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_updated_at ON public.projects(updated_at DESC);

-- 3. Project Files Table (Knowledge base source files: .md, .txt, .pdf, code)
CREATE TABLE IF NOT EXISTS public.project_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    size BIGINT NOT NULL DEFAULT 0,
    mime_type TEXT NOT NULL DEFAULT 'text/plain',
    storage_path TEXT NOT NULL,
    text_content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_project_files_project_id ON public.project_files(project_id);
CREATE INDEX IF NOT EXISTS idx_project_files_user_id ON public.project_files(user_id);

-- 4. Chats Table (Conversations grouped under projects or in history)
CREATE TABLE IF NOT EXISTS public.chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES public.projects(id) ON DELETE SET NULL,
    title TEXT NOT NULL DEFAULT 'New Chat',
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ensure project_id column exists if chats already existed without it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'chats' 
        AND column_name = 'project_id'
    ) THEN
        ALTER TABLE public.chats 
            ADD COLUMN project_id UUID REFERENCES public.projects(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_chats_user_id ON public.chats(user_id);
CREATE INDEX IF NOT EXISTS idx_chats_project_id ON public.chats(project_id);
CREATE INDEX IF NOT EXISTS idx_chats_updated_at ON public.chats(updated_at DESC);

-- 5. Messages Table (Conversation turns, thoughts, and attachments)
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES public.chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL DEFAULT '',
    reasoning TEXT,
    model_name TEXT,
    reasoning_enabled BOOLEAN,
    attachments JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Migration for existing deployments (safe to re-run):
-- ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS reasoning_enabled BOOLEAN;

CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON public.messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_user_id ON public.messages(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON public.messages(created_at ASC);

-- ==============================================================================
-- Row Level Security (RLS) Configuration
-- ==============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    -- Profiles RLS
    DROP POLICY IF EXISTS "Users can manage their own profile" ON public.profiles;
    CREATE POLICY "Users can manage their own profile"
        ON public.profiles FOR ALL
        USING (auth.uid() = id)
        WITH CHECK (auth.uid() = id);

    -- Projects RLS
    DROP POLICY IF EXISTS "Users can manage their own projects" ON public.projects;
    CREATE POLICY "Users can manage their own projects"
        ON public.projects FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);

    -- Project Files RLS
    DROP POLICY IF EXISTS "Users can manage their own project files" ON public.project_files;
    CREATE POLICY "Users can manage their own project files"
        ON public.project_files FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);

    -- Chats RLS
    DROP POLICY IF EXISTS "Users can manage their own chats" ON public.chats;
    CREATE POLICY "Users can manage their own chats"
        ON public.chats FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);

    -- Messages RLS
    DROP POLICY IF EXISTS "Users can manage their own messages" ON public.messages;
    CREATE POLICY "Users can manage their own messages"
        ON public.messages FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);
END $$;

-- ==============================================================================
-- CRITICAL FOR REALTIME DELETES WITH RLS: Replica Identity Full
-- When tables have RLS enabled, Supabase Realtime requires REPLICA IDENTITY FULL
-- so the WAL stream includes the user_id column on DELETE events.
-- Without this, DELETE events are silently dropped for RLS subscribers!
-- ==============================================================================

ALTER TABLE public.profiles REPLICA IDENTITY FULL;
ALTER TABLE public.projects REPLICA IDENTITY FULL;
ALTER TABLE public.project_files REPLICA IDENTITY FULL;
ALTER TABLE public.chats REPLICA IDENTITY FULL;
ALTER TABLE public.messages REPLICA IDENTITY FULL;

-- ==============================================================================
-- Automatic User Profile Trigger (On Auth Signup)
-- ==============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
    INSERT INTO public.profiles (id, username)
    VALUES (new.id, COALESCE(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)))
    ON CONFLICT (id) DO NOTHING;
    RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================================================
-- Supabase Realtime Publication
-- ==============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'profiles') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'projects') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.projects;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'project_files') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.project_files;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'chats') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chats;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'messages') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
END $$;

-- ==============================================================================
-- Storage Bucket Setup (attachments)
-- ==============================================================================

DO $$
BEGIN
    INSERT INTO storage.buckets (id, name, public)
    VALUES ('attachments', 'attachments', true)
    ON CONFLICT (id) DO UPDATE SET public = true;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

DO $$
BEGIN
    DROP POLICY IF EXISTS "Public access to attachments" ON storage.objects;
    CREATE POLICY "Public access to attachments"
        ON storage.objects FOR SELECT
        USING (bucket_id = 'attachments');

    DROP POLICY IF EXISTS "Authenticated users can upload attachments" ON storage.objects;
    CREATE POLICY "Authenticated users can upload attachments"
        ON storage.objects FOR INSERT
        WITH CHECK (bucket_id = 'attachments' AND auth.role() = 'authenticated');

    DROP POLICY IF EXISTS "Users can update their attachments" ON storage.objects;
    CREATE POLICY "Users can update their attachments"
        ON storage.objects FOR UPDATE
        USING (bucket_id = 'attachments' AND auth.role() = 'authenticated');

    DROP POLICY IF EXISTS "Users can delete their attachments" ON storage.objects;
    CREATE POLICY "Users can delete their attachments"
        ON storage.objects FOR DELETE
        USING (bucket_id = 'attachments' AND auth.role() = 'authenticated');
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;
