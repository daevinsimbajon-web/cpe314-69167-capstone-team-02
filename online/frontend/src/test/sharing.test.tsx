import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StatisticsSharing, SyncStatus } from '../analytics/sharing';
import { api } from '../api';
vi.mock('../api',()=>({api:vi.fn()}));
const rj={id:'request-rj',displayName:'RJ',avatar:{version:1,body:0,eyes:0,iris:0,wings:0,marking:0,accessory:0,expression:0},status:'PENDING'};
let pending=[rj],active:typeof pending=[];
beforeEach(()=>{pending=[rj];active=[];vi.mocked(api).mockImplementation(async(path,options)=>{
  if(path==='/analytics/sharing')return structuredClone({pending,active});
  expect(options?.method).toBe('POST');
  if(path.endsWith('/allow')){active=[{...rj,status:'ALLOWED'}];pending=[];}
  else if(path.endsWith('/deny'))pending=[];
  else if(path.endsWith('/disconnect'))active=[];
});});
it('shows a compact avatar request, allows sharing, confirms disconnect and removes the revoked connection',async()=>{
  render(<StatisticsSharing/>);const user=userEvent.setup();await screen.findByRole('region',{name:'Statistics Sharing Requests'});
  expect(screen.getByText(/This includes your game results/)).toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Allow RJ'}));
  await screen.findByRole('region',{name:'Active Sharing'});expect(screen.queryByRole('region',{name:'Statistics Sharing Requests'})).not.toBeInTheDocument();
  await user.click(screen.getByRole('button',{name:'Disconnect RJ'}));expect(screen.getByRole('dialog')).toHaveTextContent('Stop sharing your updated statistics with RJ?');
  await user.click(screen.getByRole('button',{name:'Cancel'}));expect(active).toHaveLength(1);
  await user.click(screen.getByRole('button',{name:'Disconnect RJ'}));await user.click(screen.getByRole('button',{name:'Disconnect'}));
  await waitFor(()=>expect(screen.queryByRole('region',{name:'Active Sharing'})).not.toBeInTheDocument());
});
it('denies requests without creating active permissions and hides empty sections',async()=>{
  render(<StatisticsSharing/>);const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Deny RJ'}));
  await waitFor(()=>expect(screen.queryByRole('region',{name:'Statistics Sharing Requests'})).not.toBeInTheDocument());expect(screen.queryByRole('region',{name:'Active Sharing'})).not.toBeInTheDocument();
});
it('keeps the request visible after server failure and offers retry',async()=>{
  const original=vi.mocked(api).getMockImplementation()!;vi.mocked(api).mockImplementation(async(path,options)=>{if(path.endsWith('/allow'))throw Error('Connection lost');return original(path,options);});
  render(<StatisticsSharing/>);const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Allow RJ'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Connection lost');expect(screen.getByRole('button',{name:'Allow RJ'})).toBeEnabled();expect(pending).toHaveLength(1);
});
it('explains a self-import instead of silently hiding the auto-update action',async()=>{
  const onRequest=vi.fn();render(<SyncStatus player={{id:'own-export',label:'Justine',games:[],exportedAt:'2026-11-17T00:00:00Z',verified:true,status:'SNAPSHOT',canRequest:false}} onRequest={onRequest} busy={false}/>);
  expect(screen.getByRole('button',{name:'Request Auto-Update'})).toBeDisabled();expect(screen.getByText(/This is your own account/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Request Auto-Update'}));expect(onRequest).not.toHaveBeenCalled();
});
it('keeps the request active for another verified player',async()=>{
  const onRequest=vi.fn();render(<SyncStatus player={{id:'other-export',label:'RJ',games:[],exportedAt:'2026-11-17T00:00:00Z',verified:true,status:'SNAPSHOT',canRequest:true}} onRequest={onRequest} busy={false}/>);
  expect(screen.getByRole('button',{name:'Request Auto-Update'})).toBeEnabled();await userEvent.setup().click(screen.getByRole('button',{name:'Request Auto-Update'}));expect(onRequest).toHaveBeenCalledOnce();expect(screen.queryByText(/This is your own account/)).not.toBeInTheDocument();
});
