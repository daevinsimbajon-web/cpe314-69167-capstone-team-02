import {useEffect,useId,useRef,useState,type KeyboardEvent} from 'react';
import {ChevronDown} from 'lucide-react';

export interface CategoryOption{id:number;name:string;questionCount?:number}
/** Native mobile picker; bounded desktop listbox with standard selection keyboard behavior. */
export function CategorySelect({value,onChange,options,disabled=false,local=false}:{value:string;onChange:(value:string)=>void;options:CategoryOption[];disabled?:boolean;local?:boolean}){
  const id=useId(),root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),[open,setOpen]=useState(false),[active,setActive]=useState(0),[mobile,setMobile]=useState(()=>window.matchMedia('(max-width:760px)').matches);
  const entries=[{id:'',name:'Any Category'},...options.map(option=>({id:String(option.id),name:option.name+(local?` (${option.questionCount??0})`:'')}))];
  const selected=entries.find(option=>option.id===value)??entries[0];
  const [popup,setPopup]=useState({up:false,height:280});
  useEffect(()=>{const media=window.matchMedia('(max-width:760px)'),update=()=>{setMobile(media.matches);setOpen(false);};media.addEventListener('change',update);return()=>media.removeEventListener('change',update);},[]);
  useEffect(()=>{if(!open)return;root.current?.querySelector<HTMLElement>(`[data-option="${active}"]`)?.focus();},[open,active]);
  useEffect(()=>{if(!open)return;const outside=(event:PointerEvent)=>{if(!root.current?.contains(event.target as Node))setOpen(false);};document.addEventListener('pointerdown',outside);return()=>document.removeEventListener('pointerdown',outside);},[open]);
  function close(){setOpen(false);trigger.current?.focus();}
  function measure(){const box=trigger.current?.getBoundingClientRect();if(!box)return;const below=window.innerHeight-box.bottom-16,above=box.top-16,up=below<180&&above>below;setPopup({up,height:Math.max(44,Math.min(280,up?above:below))});}
  function keyboard(event:KeyboardEvent){
    if(event.key==='Escape'){event.preventDefault();close();return;}
    if(event.key==='Tab'){setOpen(false);return;}
    if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){
      event.preventDefault();if(!open)measure();setOpen(true);setActive(index=>event.key==='Home'?0:event.key==='End'?entries.length-1:(index+(event.key==='ArrowDown'?1:-1)+entries.length)%entries.length);
    }else if(open&&event.key==='Enter'){event.preventDefault();onChange(entries[active].id);close();}
    else if(open&&event.key.length===1&&/\S/.test(event.key)){const next=entries.findIndex((entry,index)=>index>active&&entry.name.toLowerCase().startsWith(event.key.toLowerCase()));if(next>=0){event.preventDefault();setActive(next);}}
  }
  return <div className="category-picker" ref={root} onKeyDown={keyboard}><label id={`${id}-label`} htmlFor={mobile?`${id}-native`:undefined}>Category</label>
    {mobile?<div className="native-category"><select id={`${id}-native`} value={value} disabled={disabled} onChange={event=>onChange(event.target.value)}>{entries.map(entry=><option key={entry.id} value={entry.id}>{entry.name}</option>)}</select><ChevronDown size={18} aria-hidden="true"/></div>
      :<><button type="button" className="category-trigger" ref={trigger} role="combobox" aria-labelledby={`${id}-label`} aria-expanded={open} aria-controls={`${id}-list`} aria-haspopup="listbox" disabled={disabled} onClick={()=>{measure();setActive(Math.max(0,entries.findIndex(entry=>entry.id===value)));setOpen(!open);}}><span id={`${id}-selection`}>{selected.name}</span><ChevronDown size={18} aria-hidden="true"/></button>
        {open&&<div className={`category-options ${popup.up?'opens-up':''}`} style={{maxHeight:popup.height}} id={`${id}-list`} role="listbox" aria-labelledby={`${id}-label`}>{entries.map((entry,index)=><button type="button" role="option" aria-selected={entry.id===value} data-option={index} key={entry.id} tabIndex={index===active?0:-1} onClick={()=>{onChange(entry.id);close();}}>{entry.name}{entry.id===value&&<span aria-hidden="true">✓</span>}</button>)}</div>}</>}
  </div>;
}
