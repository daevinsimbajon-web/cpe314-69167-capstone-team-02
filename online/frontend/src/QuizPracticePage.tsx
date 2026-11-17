import {useEffect,useState} from 'react';
import {Link,useParams,useSearchParams} from 'react-router-dom';
import {api,ApiError} from './api';
import {PageHeading,Loading} from './App';
import {NightIcon} from './components/ui/NightIcon';

interface Practice {
  id:string;version:number;score:number;answered:number;total:number;
  question:string|null;choices:string[];type:string|null;canUndo:boolean;
  feedback:string|null;lastCorrect:boolean|null;lastPoints:number|null;
}

export function QuizPracticePage(){
  const {id}=useParams(),[params,setParams]=useSearchParams(),session=params.get('session');
  const [practice,setPractice]=useState<Practice|null>(null),[busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(!!session),[error,setError]=useState(''),[notice,setNotice]=useState('');
  useEffect(()=>{
    if(!session)return;
    let active=true;setLoading(true);
    api<Practice>(`/practice/${session}`).then(result=>{
      if(active)setPractice(previous=>previous?.id===result.id&&previous.version>result.version?previous:result);
    }).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[session]);
  async function start(){
    setBusy(true);setError('');setNotice('');
    try{
      const result=await api<Practice>(`/quizzes/${id}/practice`,{method:'POST'});
      setPractice(result);setParams({session:result.id},{replace:true});
    }catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  async function act(selectedIndex?:number){
    if(!practice||busy)return;
    setBusy(true);setError('');setNotice('');
    try{
      const result=await api<Practice>(`/practice/${practice.id}/${selectedIndex===undefined?'undo':'answers'}`,{
        method:'POST',body:JSON.stringify({version:practice.version,actionId:crypto.randomUUID(),
          ...(selectedIndex===undefined?{}:{selectedIndex})}),
      });
      setPractice(result);
      setNotice(selectedIndex===undefined?'Last scored answer undone. Its question is ready again.':
        result.lastCorrect?`Correct! +${result.lastPoints} points.`:`The correct answer was ${result.feedback}.`);
    }catch(e){
      setError((e as Error).message);
      if(e instanceof ApiError&&e.status===409){
        try{setPractice(await api<Practice>(`/practice/${practice.id}`));}catch{/* Keep the original conflict visible. */}
      }
    }finally{setBusy(false);}
  }
  return <div className="quiz-practice">
    <PageHeading motif="question" title="Quiz practice" subtitle="Try your questions before you host."/>
    <div className="actions"><Link className="button small" to={`/bank/${id}/preview`}>Back to quiz</Link></div>
    <p className="help-text">Personal practice · Scores here do not affect multiplayer rankings or analytics.</p>
    <p className="help-text">Practice scoring: 10 points for Multiple Choice, 5 for True / False.</p>
    {error&&<p className="error" role="alert">{error}</p>}
    {loading?<Loading/>:!practice?<section className="panel narrow"><h2>Ready for a rehearsal?</h2>
      <p>Answer in order, earn practice points, and undo a scored answer whenever you want to review it.</p>
      <button className="button primary" disabled={busy} onClick={()=>void start()}>Start practice</button>
    </section>:<section className="panel narrow" aria-busy={busy}>
      <div className="practice-summary"><strong>{practice.score} practice points</strong>
        <span>{practice.answered} / {practice.total} answered</span></div>
      <p role="status" aria-live="polite">{notice}</p>
      {practice.question?<><span className="eyebrow">Question {practice.answered+1} · {practice.type==='TRUE_FALSE'?'True / False':'Multiple Choice'}</span>
        <h2>{practice.question}</h2><div className="practice-answers">{practice.choices.map((choice,index)=>
          <button key={`${practice.version}:${index}`} className="button" disabled={busy} onClick={()=>void act(index)}>
            <span className="answer-letter" aria-hidden="true">{String.fromCharCode(65+index)}</span>{choice}
          </button>)}</div></>:<><h2>Practice complete</h2><p>You scored {practice.score} points. Review your last answer or start another rehearsal.</p></>}
      <div className="actions practice-controls"><button className="button" disabled={busy||!practice.canUndo} onClick={()=>void act()}>
        <NightIcon motif="history" size={23}/>Undo last scored answer</button>
        {!practice.question&&<button className="button primary" disabled={busy} onClick={()=>void start()}>Practice again</button>}
      </div>
    </section>}
  </div>;
}
