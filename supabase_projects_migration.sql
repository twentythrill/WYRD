-- ==============================================================================
-- WYRD: Projects Feature Migration Script
-- Run this in Supabase SQL Editor if you already have profiles, chats, and messages.
-- It safely adds projects, project_files, and the project_id column to chats,
-- and enables REPLICA IDENTITY FULL for real-time deletes.
-- ==============================================================================

-- 1. Create Projects Table if not exists
CREATE TABLE IF NOT EXISTS public.projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    system_prompt TEXT NOT NULL DEFAULT '',
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON public.projects(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_updated_at ON public.projects(updated_at DESC);

-- 2. Create Project Files Table if not exists
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

-- 3. Safely Create Chats Table if it does not exist yet
CREATE TABLE IF NOT EXISTS public.chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES public.projects(id) ON DELETE SET NULL,
    title TEXT NOT NULL DEFAULT 'New Chat',
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ensure project_id column exists on chats
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

CREATE INDEX IF NOT EXISTS idx_chats_project_id ON public.chats(project_id);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chats ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    DROP POLICY IF EXISTS "Users can manage their own projects" ON public.projects;
    CREATE POLICY "Users can manage their own projects"
        ON public.projects FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);

    DROP POLICY IF EXISTS "Users can manage their own project files" ON public.project_files;
    CREATE POLICY "Users can manage their own project files"
        ON public.project_files FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);

    DROP POLICY IF EXISTS "Users can manage their own chats" ON public.chats;
    CREATE POLICY "Users can manage their own chats"
        ON public.chats FOR ALL
        USING (auth.uid() = user_id)
        WITH CHECK (auth.uid() = user_id);
END $$;

-- 5. CRITICAL: Replica Identity Full for Realtime Deletes
ALTER TABLE public.projects REPLICA IDENTITY FULL;
ALTER TABLE public.project_files REPLICA IDENTITY FULL;
ALTER TABLE public.chats REPLICA IDENTITY FULL;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'messages') THEN
        ALTER TABLE public.messages REPLICA IDENTITY FULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'profiles') THEN
        ALTER TABLE public.profiles REPLICA IDENTITY FULL;
    END IF;
END $$;

-- 6. Add to Realtime Publication
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'projects') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.projects;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'project_files') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.project_files;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'chats') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chats;
    END IF;
END $$;
