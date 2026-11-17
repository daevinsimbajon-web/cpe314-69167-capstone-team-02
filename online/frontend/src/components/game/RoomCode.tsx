import { useEffect, useRef, useState } from 'react';
import { Check, Copy } from 'lucide-react';

export function RoomCode({code,testId}:{code:string;testId?:string}) {
  const [copied,setCopied]=useState(false),[error,setError]=useState('');
  const timer=useRef<ReturnType<typeof setTimeout>|undefined>(undefined);
  useEffect(()=>()=>clearTimeout(timer.current),[]);
  async function copy() {
    setError('');
    try {
      try {
        if(!navigator.clipboard?.writeText)throw new Error('Clipboard unavailable');
        await navigator.clipboard.writeText(code);
      } catch {
        const focused=document.activeElement as HTMLElement|null;
        const selection=window.getSelection();
        const ranges=selection?Array.from({length:selection.rangeCount},(_,i)=>selection.getRangeAt(i).cloneRange()):[];
        const input=document.createElement('textarea');
        input.value=code;input.readOnly=true;input.style.cssText='position:fixed;left:-9999px;top:0;font-size:16px';
        document.body.appendChild(input);
        try {input.focus({preventScroll:true});input.select();input.setSelectionRange(0,code.length);if(!document.execCommand('copy'))throw new Error('Copy failed');}
        finally {input.remove();focused?.focus({preventScroll:true});selection?.removeAllRanges();ranges.forEach(range=>selection?.addRange(range));}
      }
      setCopied(true);clearTimeout(timer.current);timer.current=setTimeout(()=>setCopied(false),2000);
    } catch {setCopied(false);setError('Could not copy. Select the room code and copy it manually.');}
  }
  return <span className="room-code-share"><span data-testid={testId}>{code}</span><button type="button" className="room-code-copy tooltip" aria-label="Copy room code" title="Copy room code" data-tooltip="Copy room code" onClick={()=>void copy()}>{copied?<Check size={16} color="#86efac"/>:<Copy size={16}/>}</button><span className="room-code-feedback" role="status">{copied?'Copied!':error}</span></span>;
}
