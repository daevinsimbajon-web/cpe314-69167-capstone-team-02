import { HootlyMascot } from '../brand/HootlyMascot';
export function LoadingScreen() {
  return <div className="loading" role="status"><HootlyMascot scene="loading" size={90}/><p>Getting Hootly ready…</p><div className="skeleton"/><div className="skeleton"/><span className="sr-only">Loading</span></div>;
}
