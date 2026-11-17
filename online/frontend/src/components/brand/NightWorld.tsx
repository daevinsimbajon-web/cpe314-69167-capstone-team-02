import { memo, useId } from 'react';
import { useAtmosphereMotion } from './Atmosphere';
export type NightScene='landing'|'auth'|'dashboard'|'invite'|'lobby'|'gameplay'|'data'|'celebration'|'quiet';
const stars=[[42,90],[110,35],[180,170],[260,65],[355,125],[440,40],[520,190],[610,90],[700,20],[780,150],[840,65],[940,180],[1020,25],[1110,210],[1180,80],[640,230],[80,280],[1140,300]];
export const NightForest=memo(function NightForest({edge=false,scene=edge?'quiet':'landing'}:{edge?:boolean;scene?:NightScene}){
  const uid=useId(),{ref,paused}=useAtmosphereMotion();
  const rich=['landing','lobby','celebration'].includes(scene),forest=['landing','auth','lobby','invite'].includes(scene),moon=!['gameplay','data'].includes(scene);
  return <div ref={ref} className={`night-world night-world--${scene} ${edge?'night-world-edge':''}`} data-paused={paused||undefined} aria-hidden="true"><svg viewBox="0 0 1200 700" preserveAspectRatio={scene==='landing'?'xMidYMid slice':'xMaxYMid slice'} focusable="false">
    <defs><radialGradient id={`${uid}-halo`}><stop stopColor="#b8bbff" stopOpacity=".2"/><stop offset="1" stopColor="#8d92df" stopOpacity="0"/></radialGradient><mask id={`${uid}-crescent`}><circle cx="1010" cy="120" r="47" fill="white"/><circle cx="1030" cy="101" r="44" fill="black"/></mask></defs>
    {moon&&<g className="night-crescent" transform={scene==='landing'?'translate(-410 -35)':undefined}><circle cx="1010" cy="120" r="140" fill={`url(#${uid}-halo)`}/><circle cx="1010" cy="120" r="47" fill="#cdd2ff" mask={`url(#${uid}-crescent)`}/><circle cx="1010" cy="120" r="49" fill="#c3caff" opacity=".08" mask={`url(#${uid}-crescent)`}/></g>}
    <g className="night-stars" fill="#cbd5fb">{stars.map(([x,y],i)=><circle className="sky-star" key={i} cx={x} cy={y} r={i%4===0?1.8:1} style={{animationDelay:`-${i*1.7}s`,animationDuration:`${9+i%5*2}s`}}/>)}</g>
    {rich&&<>
      {['one','two',...(scene==='landing'?['moon']:[])].map(layer=><g key={layer} className={`sky-cloud sky-cloud-${layer}`} fill={layer==='moon'?'#777d9f':'#a8b2d4'}><path d="M-115 14q5-26 36-25 12-31 44-22 23-31 53-8 27-7 41 17 34-4 42 17 32-1 39 22-121 22-255-1Z"/><path d="M-80-8q12-24 39-17m44-9q27-5 39 15" fill="none" stroke="#d9dcf2" strokeWidth="1.5" opacity=".35"/></g>)}
    </>}
    {forest&&<><g className="forest-distant" fill="#28344f"><path d="M0 495Q145 415 290 485T580 470T900 485T1200 440V700H0Z"/><path d="M70 530Q75 330 54 240Q88 260 94 353Q121 318 161 308Q122 355 102 400L112 550ZM1040 540Q1055 352 1020 240Q1058 256 1081 370Q1124 333 1159 345Q1109 384 1089 435L1094 570Z"/></g>
    <path className="forest-mid" d="M0 590Q160 506 310 560T630 565T970 543T1200 566V700H0Z" fill="#17243a"/>
    <g className="forest-boughs" fill="#111c30"><path d="M-40 700Q45 470 22 275L-10 148Q39 193 60 295Q110 281 168 221Q145 287 70 330Q80 364 68 435Q139 401 203 412Q115 439 65 481L56 700ZM1174 700Q1136 533 1165 398Q1105 364 1053 302Q1118 336 1176 354Q1194 281 1168 192Q1200 216 1218 298L1245 700Z"/></g>
    {rich&&<path className="forest-mist" d="M80 559Q340 527 600 558T1130 556" fill="none" stroke="#9ea8d9" strokeWidth="18" opacity=".035"/>}</>}
    {scene==='dashboard'&&<path d="M0 220Q70 140 145 165M50 177q-12-48-4-83M95 157q40-40 78-35" fill="none" stroke="#9d91d0" strokeWidth="5" opacity=".1"/>}
    {scene==='invite'&&<g transform="translate(1020 290)" stroke="#d3b482" opacity=".25"><path d="M0-40V0m-12 0h24l6 35h-36ZM-16 35q16 10 32 0" fill="#d3b48212"/><path d="M0 8v17" strokeWidth="4"/></g>}
    {scene==='data'&&<path d="m850 80 56 38 70-20 44 49 83-35" fill="none" stroke="#aea0df" strokeWidth="1" opacity=".15"/>}
  </svg></div>;
});
export function Campfire({small=false}:{small?:boolean}){
  const {ref,paused}=useAtmosphereMotion();
  return <div ref={ref} className={`campfire ${small?'campfire-small':''}`} data-paused={paused||undefined} aria-hidden="true"><svg viewBox="0 0 160 150" focusable="false"><ellipse cx="80" cy="126" rx="68" ry="18" fill="#f6aa4220"/><ellipse cx="80" cy="129" rx="46" ry="9" fill="#090f2080"/><g stroke="#8d6246" strokeWidth="12" strokeLinecap="round"><path d="M46 132L113 112"/><path d="M49 112L115 134"/></g><g stroke="#c9955a" strokeWidth="2" opacity=".65"><path d="M49 130L110 113"/><path d="M50 110L112 132"/></g><path className="flame flame-outer" d="M80 123C35 125 39 87 58 69C57 92 68 87 70 75C70 53 88 38 83 17C119 49 103 67 114 82C128 106 108 128 80 123Z" fill="#e68b3e"/><path className="flame flame-middle" d="M78 121C53 124 55 104 68 89C66 105 80 100 77 86C75 73 91 66 90 54C112 83 100 88 106 101C111 117 95 124 78 121Z" fill="#fbb952"/><path className="flame flame-inner" d="M82 122C68 125 68 111 77 102C77 113 84 102 86 94C99 107 100 123 82 122Z" fill="#ffe8a0"/><circle className="ember ember-one" cx="68" cy="52" r="2" fill="#ffd284"/><circle className="ember ember-two" cx="102" cy="45" r="1.6" fill="#ffd284"/></svg></div>;
}
