import { useId, useRef } from 'react';
import { motion } from 'motion/react';
export function Tabs({items,value,onChange}:{items:{value:string;label:string}[];value:string;onChange:(value:string)=>void}) {
  const id=useId(),buttons=useRef<(HTMLButtonElement|null)[]>([]);
  return <div className="tabs" role="tablist" aria-label="Question collection tools">{items.map((item,index)=><button ref={el=>{buttons.current[index]=el;}} type="button" key={item.value} role="tab" id={`tab-${item.value}`} aria-controls={`panel-${item.value}`} aria-selected={value===item.value} tabIndex={value===item.value?0:-1} className="tab" onClick={()=>onChange(item.value)} onKeyDown={e=>{
    const next=e.key==='ArrowRight'?(index+1)%items.length:e.key==='ArrowLeft'?(index+items.length-1)%items.length:e.key==='Home'?0:e.key==='End'?items.length-1:-1;
    if(next>=0){e.preventDefault();onChange(items[next].value);buttons.current[next]?.focus();}
  }}>{item.label}{value===item.value&&<motion.span layoutId={`tab-indicator-${id}`} className="tab-indicator"/>}</button>)}</div>;
}
