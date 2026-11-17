import { useEffect, useRef } from 'react';
import { AnimatedButton } from './AnimatedButton';
export function ConfirmDialog({open,onCancel,onConfirm,title,description,cancelLabel='Keep playing',confirmLabel='Confirm',eyebrow='HOST CONTROLS'}:{open:boolean;onCancel:()=>void;onConfirm:()=>void;title:string;description:string;cancelLabel?:string;confirmLabel?:string;eyebrow?:string}) {
  const ref=useRef<HTMLDialogElement>(null);
  useEffect(()=>{if(open&&!ref.current?.open)ref.current?.showModal();else if(!open)ref.current?.close();},[open]);
  return <dialog ref={ref} className="confirm-dialog" onCancel={e=>{e.preventDefault();onCancel();}} onClick={e=>{if(e.target===e.currentTarget)onCancel();}} aria-labelledby="confirm-title" aria-describedby="confirm-description">
    <span className="eyebrow">{eyebrow}</span><h2 id="confirm-title">{title}</h2><p id="confirm-description">{description}</p><div className="actions"><AnimatedButton onClick={onCancel}>{cancelLabel}</AnimatedButton><AnimatedButton className="danger" onClick={onConfirm}>{confirmLabel}</AnimatedButton></div>
  </dialog>;
}
