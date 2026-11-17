import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { BRAND_ASSETS, hasBrandAsset } from '../../config/mascotScenes';

const AssetsContext = createContext<ReadonlySet<string> | null>(null);
const knownPaths = Object.values(BRAND_ASSETS);

/** Public assets may be added after Vite started; a module glob is only an initial hint. */
export function BrandAssetsProvider({ children }: { children: ReactNode }) {
  const [assets, setAssets] = useState<ReadonlySet<string>>(() => new Set(knownPaths.filter(hasBrandAsset)));
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    async function refresh() {
      try {
        const response = await fetch('/brand/assets.json', { cache: 'no-store', signal: controller.signal });
        if (!response.ok) return;
        const data: unknown = await response.json();
        if (!active || !Array.isArray(data)) return;
        const next = new Set<string>(knownPaths.filter(path => data.includes(path)));
        setAssets(current => current.size === next.size && [...current].every(path => next.has(path)) ? current : next);
      } catch { /* Missing/unavailable manifest keeps the initial build snapshot. */ }
    }
    void refresh();
    window.addEventListener('focus', refresh);
    const retry = import.meta.env.DEV ? setInterval(() => void refresh(), 5000) : undefined;
    return () => { active = false; controller.abort(); clearInterval(retry); window.removeEventListener('focus', refresh); };
  }, []);
  return <AssetsContext.Provider value={assets}>{children}</AssetsContext.Provider>;
}

export function useBrandAsset(path: string) {
  const assets = useContext(AssetsContext);
  return assets ? assets.has(path) : hasBrandAsset(path);
}
