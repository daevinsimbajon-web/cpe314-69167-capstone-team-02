import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { NightIcon } from '../components/ui/NightIcon';
export interface ChartSeries {label:string;color:string;values:(number|null)[]}
export const chartColors=['#c4b5fd','#67e8f9','#fcd34d','#fda4af','#a7f3d0','#cbd5e1'];
const format=(value:number|null)=>value===null?'Unavailable':Number(value.toFixed(1)).toLocaleString();
export function Chart({title,axis,labels,series,line=false,max,description,controls}:{title:string;axis:string;labels:string[];series:ChartSeries[];line?:boolean;max?:number;description?:string;controls?:ReactNode}){
  const id=useId(),[selected,setSelected]=useState<number|null>(null);
  const lineRef=useRef<HTMLDivElement>(null),[width,setWidth]=useState(600);
  useEffect(()=>{if(!lineRef.current||typeof ResizeObserver==='undefined')return;const observer=new ResizeObserver(entries=>setWidth(Math.max(180,entries[0].contentRect.width)));observer.observe(lineRef.current);return()=>observer.disconnect();},[line,labels.length]);
  const maximum=max??Math.max(1,...series.flatMap(s=>s.values.map(v=>v??0)));
  const selectedIndex=selected!==null&&selected<labels.length?selected:null;
  useEffect(()=>setSelected(null),[labels.join('\u0000'),JSON.stringify(series)]);
  return <section className="panel chart-card" aria-labelledby={id}><div className="chart-heading"><h2 id={id}><NightIcon motif="analytics" size={25}/>{title}</h2><span className="chart-unit">{axis}</span>{controls}</div>{description&&<p className="help-text">{description}</p>}
    <ul className="chart-legend" aria-label={`${title} legend`}>{series.map((s,i)=><li key={`${s.label}:${i}`}><i style={{background:s.color}}/><span>{i+1}. {s.label}</span></li>)}</ul>
    {!labels.length?<p className="chart-empty">No data in the selected period.</p>:line?<div className="line-chart" ref={lineRef}><svg viewBox={`0 0 ${width} 240`} role="img" aria-label={`${title}. Dates on the horizontal axis; ${axis} on the vertical axis. Exact values are in the data table below.`}>
      {[0,.5,1].map(v=><g key={v}><line x1="48" x2={width-20} y1={195-v*160} y2={195-v*160} stroke="#c4b5fd25"/><text x="40" y={199-v*160} textAnchor="end">{format(maximum*v)}</text></g>)}
      {series.map((s,index)=>{const points=s.values.map((v,i)=>v===null?null:`${labels.length===1?width/2:48+i/(labels.length-1)*(width-68)},${195-Math.min(1,v/maximum)*160}`);return <g key={`${s.label}:${index}`}>
        {/* Missing observations break a line rather than drawing invented zero values. */}
        {points.reduce<string[]>((segments,point)=>{if(point===null)segments.push('');else segments[segments.length-1]+=` ${point}`;return segments;},['']).filter(segment=>segment.trim().split(/\s+/).length>1&&segment.trim()).map((segment,j)=><polyline key={j} points={segment} fill="none" stroke={s.color} strokeWidth="3" strokeDasharray={[undefined,'8 4','3 3','12 3 3 3'][Math.floor(index/6)%4]}/>)}
        {s.values.map((v,i)=>v===null?null:<circle key={i} cx={labels.length===1?width/2:48+i/(labels.length-1)*(width-68)} cy={195-Math.min(1,v/maximum)*160} r="4" fill={s.color}><title>{labels[i]}: {s.label} {format(v)} {axis}</title></circle>)}
      </g>;})}
      {labels.map((label,i)=>i===0||i===labels.length-1||width>500&&labels.length<6?<text key={label} x={labels.length===1?width/2:48+i/(labels.length-1)*(width-68)} y="220" textAnchor={i===0?'start':i===labels.length-1?'end':'middle'}>{width<400&&label.length===10?label.slice(5):label}</text>:null)}
    </svg><p className="axis-label">Date (UTC)</p><label className="chart-point-picker">Inspect date<select aria-label={`Inspect date for ${title}`} value={selectedIndex??''} onChange={e=>setSelected(e.target.value===''?null:Number(e.target.value))}><option value="">Choose a date for exact values</option>{labels.map((label,i)=><option key={`${label}:${i}`} value={i}>{label}</option>)}</select></label></div>:<div className="bar-chart" role="group" aria-label={`${title}: categories and ${axis}`}>
      {labels.map((label,i)=><button key={`${label}:${i}`} className="chart-bar-row" aria-pressed={selectedIndex===i} onClick={()=>setSelected(selectedIndex===i?null:i)}><span className="chart-category">{label}</span><span className="bar-series">{series.map((s,j)=><span className="bar-measure" key={`${s.label}:${j}`}><span className="bar-track"><span style={{width:`${Math.max(0,Math.min(100,(s.values[i]??0)/maximum*100))}%`,background:s.color}}/></span><strong>{format(s.values[i])}<small className="sr-only"> {axis}, series {j+1} {s.label}</small></strong></span>)}</span></button>)}
      <p className="axis-label">0 — {format(maximum)} {axis} · Tap a row for details</p>
    </div>}
    {selectedIndex!==null&&<p className="chart-readout" role="status"><strong>{labels[selectedIndex]}</strong>{series.map((s,j)=><span key={`${s.label}:${j}`}>{s.label}: {format(s.values[selectedIndex])} {axis}</span>)}</p>}
    {labels.length>0&&<details className="chart-table"><summary>View data table and exact values</summary><div className="table-wrap" tabIndex={0} aria-label={`${title} data, scroll horizontally if needed`}><table><thead><tr><th scope="col">{line?'Date (UTC)':'Category / player'}</th>{series.map((s,j)=><th scope="col" key={`${s.label}:${j}`}>{s.label} ({axis})</th>)}</tr></thead><tbody>{labels.map((label,i)=><tr key={`${label}:${i}`}><th scope="row">{label}</th>{series.map((s,j)=><td key={`${s.label}:${j}`}>{format(s.values[i])}</td>)}</tr>)}</tbody></table></div></details>}
  </section>;
}
