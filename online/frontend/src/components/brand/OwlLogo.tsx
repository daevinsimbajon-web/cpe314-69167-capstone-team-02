import { OwlArtwork } from './OwlArtwork';

export function OwlLogo({ iconOnly = false, theme = 'dark', className = '' }: {iconOnly?:boolean;theme?:'light'|'dark';className?:string}) {
  return <span className={`owl-logo owl-logo-${theme} ${className}`}>
    <OwlArtwork className="owl-logo-mark" aria-hidden="true"/>
    {iconOnly ? <span className="sr-only">Quiz Night Host</span> : <span className="owl-wordmark">quiz night<span>HOST · PLAY · CONNECT</span></span>}
  </span>;
}
