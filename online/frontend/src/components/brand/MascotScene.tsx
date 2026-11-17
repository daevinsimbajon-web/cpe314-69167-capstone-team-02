import { HootlyMascot, type HootlyMascotProps } from './HootlyMascot';

/** Composed scenes keep presentation independent of forms and authoritative room data. */
export function MascotScene({ caption, ...props }: HootlyMascotProps & { caption?: string }) {
  return <div className={`mascot-scene mascot-scene-${props.scene}`}><HootlyMascot {...props}/>{caption && <p className="mascot-scene-caption">{caption}</p>}</div>;
}
