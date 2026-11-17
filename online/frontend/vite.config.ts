import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { loadEnv } from 'vite';
import { publicOrigins, proxyHeaders } from './devProxy';

const brandPaths = ['/brand/hootly-logo.png', '/brand/hootly-owl-icon.png', '/favicon.ico', '/apple-touch-icon.png'];
const availableBrandPaths = () => brandPaths.filter(path => existsSync(fileURLToPath(new URL(`./public${path}`, import.meta.url))));

export default defineConfig(({mode}) => {
 const environment=loadEnv(mode,fileURLToPath(new URL('.',import.meta.url)),'HOOTLY_');
 const approvedOrigins=publicOrigins(environment.HOOTLY_PUBLIC_ORIGINS??'');
 const backend=process.env.HOOTLY_BACKEND_URL??'http://127.0.0.1:8080';
 const configure=(proxy:import('vite').HttpProxy.ProxyServer)=>{
   proxy.on('proxyReq',(outgoing,incoming)=>proxyHeaders(outgoing,incoming,approvedOrigins));
   proxy.on('proxyReqWs',(outgoing,incoming)=>proxyHeaders(outgoing,incoming,approvedOrigins));
 };
 return {
  define: { 'import.meta.env.HOOTLY_BRAND_FILES': JSON.stringify(availableBrandPaths()) },
  plugins: [react(), tailwindcss(), {
    name: 'hootly-approved-brand-metadata',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if (request.url?.split('?')[0] !== '/brand/assets.json') return next();
        response.setHeader('Content-Type', 'application/json');
        response.setHeader('Cache-Control', 'no-store');
        response.end(JSON.stringify(availableBrandPaths()));
      });
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'brand/assets.json', source: JSON.stringify(availableBrandPaths()) });
    },
    // Never advertise a missing social image or request an absent icon.
    // The source HTML becomes active automatically once approved files exist.
    transformIndexHtml(html) {
      return html.replace(/<(?:link|meta)\s[^>]+>/g, tag => {
        const path = tag.match(/(?:href|content)="(\/(?:brand\/hootly-logo\.png|favicon\.ico|apple-touch-icon\.png))"/)?.[1];
        if (path && !existsSync(fileURLToPath(new URL(`./public${path}`, import.meta.url)))) {
          // An explicit empty icon also prevents browsers requesting /favicon.ico implicitly.
          return path === '/favicon.ico' ? '<link rel="icon" href="data:,"/>' : '';
        }
        return tag;
      });
    },
  }],
  server: { port: 5173, strictPort: true, allowedHosts:approvedOrigins.map(origin=>new URL(origin).hostname), proxy: {
    '/api': { target: backend, changeOrigin: false, xfwd:false, configure },
    '/ws': { target: backend.replace(/^http/,'ws'), ws: true, changeOrigin: false, xfwd:false, configure },
  } },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], exclude: ['e2e/**', 'node_modules/**'] },
 };
});
