import {afterEach,expect,it,vi} from 'vitest';
import {render,screen,within} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HistoryPage} from '../RoomPages';
vi.mock('../context',()=>({useAuth:()=>({user:{id:'host'}}),useToast:()=>vi.fn()}));
afterEach(()=>vi.unstubAllGlobals());
it('shows hosted games without inventing a player score and links to their results',async()=>{
  vi.stubGlobal('fetch',vi.fn(async()=>Response.json([
    {roomCode:'HOST1234',title:'Hosted quiz',finishedAt:'2026-11-17T09:00:00Z',points:null,rank:null,hosted:true},
    {roomCode:'PLAY1234',title:'Played quiz',finishedAt:'2026-11-17T08:00:00Z',points:10,rank:1,hosted:false},
    {roomCode:'OLDH1234',title:'Older hosted quiz',finishedAt:'2026-11-16T08:00:00Z',points:10,rank:1,hosted:true}
  ])));
  render(<MemoryRouter><HistoryPage/></MemoryRouter>);
  const hostRow=(await screen.findByText('Hosted quiz')).closest('tr')!;
  expect(within(hostRow).getByText('Host')).toBeInTheDocument();
  expect(within(hostRow).getAllByText('—')).toHaveLength(1);
  expect(within(hostRow).getAllByRole('cell')[3]).toHaveTextContent('Host');
  expect(within(hostRow).getByRole('link',{name:/Results/})).toHaveAttribute('href','/rooms/HOST1234');
  const playerRow=screen.getByText('Played quiz').closest('tr')!;
  expect(within(playerRow).getByText('10')).toBeInTheDocument();expect(within(playerRow).getByText('#1')).toBeInTheDocument();
  const olderRow=screen.getByText('Older hosted quiz').closest('tr')!;
  expect(within(olderRow).getAllByRole('cell')[3]).toHaveTextContent('Host');
});
