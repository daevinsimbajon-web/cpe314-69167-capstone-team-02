import { useRef, type ClipboardEvent } from 'react';

/** One controlled code, six labelled inputs; supports native one-time-code autofill. */
export function CodeInput({value,onChange,disabled=false}:{value:string;onChange:(code:string)=>void;disabled?:boolean}){
  const refs=useRef<(HTMLInputElement|null)[]>([]);
  function insert(text:string,index:number){
    const digits=text.replace(/\D/g,'');if(!digits)return;
    const next=(digits.length>=6?digits.slice(0,6):value.padEnd(6,' ').slice(0,index)+digits+value.padEnd(6,' ').slice(index+digits.length)).slice(0,6);
    onChange(next.trimEnd());refs.current[Math.min(5,digits.length>=6?5:index+digits.length)]?.focus();
  }
  function paste(event:ClipboardEvent<HTMLInputElement>,index:number){event.preventDefault();insert(event.clipboardData.getData('text'),index);}
  return <fieldset className="email-code"><legend>Six-digit verification code</legend><div>{[0,1,2,3,4,5].map(index=><input key={index} ref={node=>{refs.current[index]=node;}} aria-label={`Digit ${index+1}`} type="text" inputMode="numeric" autoComplete={index===0?'one-time-code':'off'} pattern="[0-9]" value={value[index]?.trim()||''} disabled={disabled} onPaste={event=>paste(event,index)} onFocus={event=>event.target.select()} onChange={event=>{if(event.target.value)insert(event.target.value,index);else onChange((value.padEnd(6,' ').slice(0,index)+' '+value.slice(index+1)).trimEnd());}} onKeyDown={event=>{
    if(event.key==='Backspace'&&!value[index]?.trim()&&index>0){event.preventDefault();onChange((value.slice(0,index-1)+' '+value.slice(index)).trimEnd());refs.current[index-1]?.focus();}
    if(event.key==='ArrowLeft')refs.current[Math.max(0,index-1)]?.focus();
    if(event.key==='ArrowRight')refs.current[Math.min(5,index+1)]?.focus();
  }}/>)}</div></fieldset>;
}
