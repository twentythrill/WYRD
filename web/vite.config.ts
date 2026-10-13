import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss()
  ],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src')
    }
  },
  define: {
    // Provide fallback support for NEXT_PUBLIC_ variables
    'process.env.NEXT_PUBLIC_SUPABASE_URL': JSON.stringify(process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://ncxbhueublthjfawkkvy.supabase.co'),
    'process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY': JSON.stringify(process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY || 'sb_publishable_lZa8oy7FwdDOKvv4Jrj26A_skhlS325'),
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('@supabase')) {
            return 'supabase'
          }
        },
      },
    },
  },
})
