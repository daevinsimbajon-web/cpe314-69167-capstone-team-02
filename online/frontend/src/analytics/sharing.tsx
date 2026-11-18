import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { PlayerOwl } from '../components/avatar/PlayerOwl';
import type { AvatarConfig } from '../components/avatar/avatarConfig';
import type { ComparedPlayer, StatsExport } from './model';
import '../styles/sharing.css';

export type SharingStatus='SNAPSHOT'|'PENDING'|'ALLOWED'|'DENIED'|'REVOKED';
export interface SavedMember {membershipId:string;snapshot:StatsExport;label:string;verified:boolean;status:SharingStatus;canRequest:boolean;lastSyncedAt:string|null}
export interface ImportJob {id:string;status:'QUEUED'|'PROCESSING'|'COMPLETED'|'FAILED';progress:number;message:string|null}
export const memberToPlayer=(m:SavedMember):ComparedPlayer=>({id:m.snapshot.player.exportPlayerId,label:m.label,username:m.snapshot.player.username,avatar:m.snapshot.player.avatar,exportedAt:m.snapshot.exportedAt,games:m.snapshot.games,membershipId:m.membershipId,verified:m.verified,status:m.status,canRequest:m.canRequest,lastSyncedAt:m.lastSyncedAt});
export async function waitForImport(job:ImportJob,progress:(job:ImportJob)=>void,signal?:AbortSignal):Promise<ImportJob> {
  while(job.status==='QUEUED'||job.status==='PROCESSING') {
    progress(job);await new Promise(resolve=>setTimeout(resolve,350));
    if(signal?.aborted)throw new DOMException('Page closed','AbortError');
    job=await api<ImportJob>(`/analytics/comparisons/import-jobs/${job.id}`,{signal});
  }return job;
}
const labels:Record<SharingStatus,string>={SNAPSHOT:'Imported Snapshot',PENDING:'Request Pending',ALLOWED:'Auto-Update Enabled',DENIED:'Request Denied',REVOKED:'Sharing Disconnected'};
export function SyncStatus({player,onRequest,busy}:{player:ComparedPlayer;onRequest:()=>void;busy:boolean}) {
  // A verified SNAPSHOT with canRequest=false is the owner's own history.
  // Pending, approved and cooldown states are represented separately by the API.
  const ownSnapshot=player.verified&&player.status==='SNAPSHOT'&&player.canRequest===false;
  return <div className={`sharing-status sharing-${player.status??'SNAPSHOT'}`} aria-live="polite"><span>{labels[player.status??'SNAPSHOT']}</span>
    {(player.canRequest||ownSnapshot)&&<button className="button small" disabled={busy||ownSnapshot} onClick={onRequest} title={ownSnapshot?'You cannot send a sharing request to your own account.':undefined}>Request Auto-Update</button>}
    {ownSnapshot&&<small>This is your own account. To request auto-updates, import another player’s signed Hootly export. Your latest statistics are in My Analytics.</small>}
    {!player.verified&&<small>Automatic updates are unavailable for this imported history.</small>}
    {player.verified&&player.status==='DENIED'&&!player.canRequest&&<small>You can request again after the seven-day cooldown.</small>}
    {player.status==='ALLOWED'&&<small>Refreshed on opening and every 15 seconds while this page is open.</small>}
  </div>;
}
interface Connection {id:string;displayName:string;avatar:AvatarConfig;status:string}
interface Inbox {pending:Connection[];active:Connection[]}
export function StatisticsSharing() {
  const [inbox,setInbox]=useState<Inbox>({pending:[],active:[]}),[error,setError]=useState(''),[busy,setBusy]=useState(false),[disconnect,setDisconnect]=useState<Connection|null>(null);
  const dialog=useRef<HTMLDialogElement>(null),version=useRef(0);
  async function refresh(){const current=++version.current;const next=await api<Inbox>('/analytics/sharing');if(current===version.current&&Array.isArray(next.pending)&&Array.isArray(next.active)){setInbox(next);setError('');}}
  useEffect(()=>{let alive=true,polling=false;const load=()=>{if(polling||document.hidden)return;polling=true;void refresh().catch(e=>{if(alive)setError(e.message);}).finally(()=>{polling=false;});};load();const timer=setInterval(load,15000);window.addEventListener('focus',load);document.addEventListener('visibilitychange',load);return()=>{alive=false;version.current++;clearInterval(timer);window.removeEventListener('focus',load);document.removeEventListener('visibilitychange',load);};},[]);
  useEffect(()=>{if(disconnect)dialog.current?.showModal();else dialog.current?.close();},[disconnect]);
  async function act(connection:Connection,action:'allow'|'deny'|'disconnect') {
    if(busy)return;setBusy(true);setError('');
    try{await api(`/analytics/sharing/${connection.id}/${action}`,{method:'POST'});setDisconnect(null);await refresh();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <>
    {error&&<p className="error sharing-error" role="alert">Statistics sharing: {error} <button className="button small" onClick={()=>void refresh().catch(e=>setError(e.message))}>Retry</button></p>}
    {inbox.pending.length>0&&<section className="panel sharing-panel" aria-label="Statistics Sharing Requests"><h2>Statistics Sharing Requests <span className="sharing-count">{inbox.pending.length}</span></h2>{inbox.pending.map(c=><div className="sharing-connection" key={c.id}><PlayerOwl identity={c.id} avatar={c.avatar} name={c.displayName} size={48}/><div><p><strong>{c.displayName}</strong> wants to automatically view your updated game statistics.</p><small>This includes your game results, points, accuracy, and performance history.</small></div><div className="actions"><button className="button small primary" disabled={busy} aria-label={`Allow ${c.displayName}`} onClick={()=>void act(c,'allow')}>Allow</button><button className="button small" disabled={busy} aria-label={`Deny ${c.displayName}`} onClick={()=>void act(c,'deny')}>Deny</button></div></div>)}</section>}
    {inbox.active.length>0&&<section className="panel sharing-panel" aria-label="Active Sharing"><h2>Active Sharing</h2>{inbox.active.map(c=><div className="sharing-connection" key={c.id}><PlayerOwl identity={c.id} avatar={c.avatar} name={c.displayName} size={48}/><div><strong>{c.displayName}</strong><p>{c.displayName} can view your updated game statistics.</p><small className="sharing-active-label">Active</small></div><button className="button small" disabled={busy} aria-label={`Disconnect ${c.displayName}`} onClick={()=>setDisconnect(c)}>Disconnect</button></div>)}<p className="help-text">You can stop future updates anytime. Original JSON imports remain with the person who imported them. Already downloaded or viewed copies cannot be erased.</p></section>}
    <dialog ref={dialog} className="confirm-dialog sharing-confirm" aria-labelledby="sharing-disconnect-title" onCancel={e=>{e.preventDefault();if(!busy)setDisconnect(null);}}><h2 id="sharing-disconnect-title">Stop sharing your updated statistics with {disconnect?.displayName}?</h2><p>This will prevent {disconnect?.displayName} from seeing your future game updates.</p><p className="help-text">Their original imported history remains available. This does not erase copies already shared.</p><div className="actions"><button className="button" disabled={busy} onClick={()=>setDisconnect(null)}>Cancel</button><button className="button danger" disabled={busy} onClick={()=>{if(disconnect)void act(disconnect,'disconnect');}}>Disconnect</button></div></dialog>
  </>;
}
