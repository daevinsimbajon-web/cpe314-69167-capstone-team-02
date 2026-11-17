import { Check, LockKeyhole, X } from 'lucide-react';
import { AnimatedButton } from '../ui/AnimatedButton';
export function AnswerCard({choice,index,selected,correct,revealed,disabled,onClick}:{choice:string;index:number;selected:boolean;correct:boolean;revealed:boolean;disabled:boolean;onClick:()=>void}) {
  const state=revealed?(correct?'correct':selected?'incorrect':''):selected?'selected':'';
  return <AnimatedButton className={`answer-card ${state}`} disabled={disabled} onClick={onClick} aria-pressed={selected}>
    <span className="answer-letter">{String.fromCharCode(65+index)}</span><span className="answer-copy">{choice}</span>
    {revealed&&correct?<Check size={20} aria-label="Correct answer"/>:revealed&&selected?<X size={20} aria-label="Incorrect answer"/>:selected?<LockKeyhole size={17} aria-label="Answer locked"/>:null}
  </AnimatedButton>;
}
