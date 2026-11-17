import { useState } from 'react';
import { BRAND_ASSETS } from '../../config/mascotScenes';
import { useBrandAsset } from './BrandAssetsProvider';

/** Approved files are served untouched. Text fallback avoids substituting another owl. */
export function HootlyLogo({ iconOnly = false, theme = 'dark', className = '', compactOnMobile = false }: { iconOnly?: boolean; theme?: 'light' | 'dark'; className?: string; compactOnMobile?: boolean }) {
  const [failed, setFailed] = useState(false);
  const iconAvailable=useBrandAsset(BRAND_ASSETS.icon),logoAvailable=useBrandAsset(BRAND_ASSETS.logo);
  const missing = failed || !(iconOnly ? iconAvailable : logoAvailable);
  return <span className={`hootly-logo hootly-logo-${theme} ${className}`}>
    {!missing ? <picture className={compactOnMobile ? 'hootly-compact-mobile' : undefined}>{compactOnMobile && iconAvailable && <source media="(max-width:760px)" srcSet={BRAND_ASSETS.icon}/>}<img key={String(iconOnly)} src={iconOnly ? BRAND_ASSETS.icon : BRAND_ASSETS.logo} alt="Hootly" className={iconOnly ? 'hootly-logo-icon' : 'hootly-logo-full'} onError={() => setFailed(true)} /></picture>
      : <span className="hootly-wordmark">Hootly</span>}
  </span>;
}
