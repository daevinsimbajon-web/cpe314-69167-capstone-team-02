import {afterEach,expect,it,vi} from 'vitest';
import {act,render,screen} from '@testing-library/react';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {RoomPage} from '../RoomPages';
vi.mock('../context',()=>({useAuth:()=>({user:{id:'player'},refresh:vi.fn()}),useToast:()=>vi.fn()}));
class Socket {
  static latest:Socket;
  onopen:(()=>void)|null=null;onclose:(()=>void)|null=null;onmessage:(()=>void)|null=null;onerror:(()=>void)|null=null;
  close=vi.fn();constructor(){Socket.latest=this;}
}
afterEach(()=>vi.unstubAllGlobals());
function mount(){vi.stubGlobal('WebSocket',Socket);render(<MemoryRouter initialEntries={['/rooms/TLFSKBMH']}><Routes><Route path="/rooms/:code" element={<RoomPage/>}/></Routes></MemoryRouter>);}
it('automatically shows a sad removal screen when the server revokes the room connection',async()=>{
  let kicked=false;
  vi.stubGlobal('fetch',vi.fn(async()=>kicked?Response.json({code:'ROOM_REMOVED',message:'Removed by the host'},{status:403}):Response.json({code:'TLFSKBMH',title:'Our lobby',status:'LOBBY',hostId:'host',questionCount:2,secondsPerQuestion:20,maxPlayers:8,participants:[],leaderboard:[],serverTime:new Date().toISOString()})));
  mount();await screen.findByRole('heading',{name:'Our lobby'});kicked=true;act(()=>Socket.latest.onclose?.());
  expect(await screen.findByRole('heading',{name:'You’ve been removed from the room.'})).toBeInTheDocument();expect(screen.getByText('Cheer up! There are more rooms waiting for you.')).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Join another room'})).toHaveAttribute('href','/join');expect(screen.getByRole('link',{name:'Back to dashboard'})).toHaveAttribute('href','/dashboard');expect(screen.queryByRole('button',{name:/Try again/i})).not.toBeInTheDocument();expect(screen.queryByText('A little connection hiccup.')).not.toBeInTheDocument();expect(document.querySelector('.hootly-character')).toHaveAttribute('data-expression','sad');expect(Socket.latest.close).toHaveBeenCalled();
});
it('does not mistake an ordinary forbidden response or network failure for a kick',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json({message:'Join this room before accessing it'},{status:403})));mount();expect(await screen.findByRole('button',{name:'Try again'})).toBeInTheDocument();expect(screen.queryByText('You’ve been removed from the room.')).not.toBeInTheDocument();
});
