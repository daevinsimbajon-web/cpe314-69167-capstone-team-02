import { NightIcon } from '../ui/NightIcon';
import { WandSparkles } from 'lucide-react';
import {useId} from 'react';

export function RetryStepper({value,onChange,disabled=false}:{value:number;onChange:(value:number)=>void;disabled?:boolean}){
  const id=useId();
  return <fieldset className="retry-setting" disabled={disabled} aria-describedby={`${id}-help ${id}-selection`}><legend><span className="field-caption"><NightIcon motif="potion" size={24}/>Retry chances</span></legend><div className="retry-segments">
    {[0,1,2,3].map(option=><label key={option}><input type="radio" name={`retry-${id}`} value={option} checked={value===option} onChange={()=>onChange(option)}/><span>{option===0?'Off':option}</span></label>)}
  </div><p id={`${id}-help`} className="help-text">Give each player extra chances to recover from wrong answers throughout the game.</p><p id={`${id}-selection`} className="help-text" role="status">{value===0?'One attempt per question.':value<=3?`Each player receives ${value} extra ${value===1?'chance':'chances'}.`:`Legacy setting: ${value} retries. Choose Off, 1, 2 or 3 to switch this lobby to the current scoring rules.`}</p></fieldset>;
}
export function RetryChances({remaining,total}:{remaining:number;total:number}){
  if(total===0)return <p className="help-text">One attempt per question. Earn up to 10 points for a correct answer.</p>;
  return <div className="retry-chances" role="status" aria-label={`${remaining} of ${total} retry chances remaining`}><div className="retry-wands" aria-hidden="true">{Array.from({length:total},(_,i)=><WandSparkles key={i} size={18}  className={i<remaining?'':'spent'}/>)}</div><span>{remaining===0?'No chances left ? keep playing!':`${remaining} ${remaining===1?'retry':'retries'} left`}<small> · for this game</small></span></div>;
}
