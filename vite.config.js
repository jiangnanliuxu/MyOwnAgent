import fs from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const spaRoutes = new Set(['/settings', '/robot-settings', '/integration']);

function agentDeskSpaFallback() {
  return {
    name: 'agent-desk-spa-fallback',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const pathname = (req.url || '').split('?')[0];
        if (!spaRoutes.has(pathname)) {
          next();
          return;
        }

        try {
          const template = fs.readFileSync(fileURLToPath(new URL('./index.html', import.meta.url)), 'utf8');
          const html = await server.transformIndexHtml(req.url, template);
          res.statusCode = 200;
          res.setHeader('Content-Type', 'text/html');
          res.end(html);
        } catch (error) {
          next(error);
        }
      });
    }
  };
}

export default defineConfig({
  plugins: [agentDeskSpaFallback(), vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true
  },
  test: {
    include: ['__tests__/**/*.test.js']
  }
});
