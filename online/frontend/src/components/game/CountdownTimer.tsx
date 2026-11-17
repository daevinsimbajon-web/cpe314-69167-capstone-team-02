export function CountdownTimer({remaining,total,reveal=false}:{remaining:number;total:number;reveal?:boolean}) {
  const seconds=Math.max(0,Math.ceil(remaining)),fraction=Math.min(1,Math.max(0,remaining/Math.max(1,total)));
  return <div className={`countdown ${seconds<=5&&!reveal?'countdown-urgent':''}`} aria-label={`Time remaining: ${seconds} seconds`}>
    <svg viewBox="0 0 64 64" aria-hidden="true"><circle className="countdown-track" cx="32" cy="32" r="27"/><circle className="countdown-value" cx="32" cy="32" r="27" strokeDasharray={170} strokeDashoffset={170*(1-fraction)}/></svg>
    <div><strong>{seconds}</strong><span>{reveal?'REVEAL':'SECONDS'}</span></div>
  </div>;
}
