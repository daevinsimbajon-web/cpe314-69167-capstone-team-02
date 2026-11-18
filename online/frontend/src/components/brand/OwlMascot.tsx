import { OwlArtwork } from './OwlArtwork';

export type OwlMood = 'idle'|'greeting'|'thinking'|'happy'|'encouraging'|'victory';
export function OwlMascot({mood='idle',size=220,className='',label}:{mood?:OwlMood;size?:number;className?:string;label?:string}) {
  return <div className={`owl-mascot owl-${mood} ${className}`} style={{width:size,maxWidth:'100%'}} data-mood={mood}>
    <OwlArtwork role={label?'img':undefined} aria-label={label} aria-hidden={label?undefined:true}/>
    <span className="owl-spark owl-spark-one" aria-hidden="true">✦</span><span className="owl-spark owl-spark-two" aria-hidden="true">✧</span>
  </div>;
}
