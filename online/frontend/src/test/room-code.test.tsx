import { afterEach, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { RoomCode } from '../components/game/RoomCode';

afterEach(()=>{vi.useRealTimers();vi.restoreAllMocks();});
it('copies only the exact code and clears its confirmation after two seconds',async()=>{
  vi.useFakeTimers();const writeText=vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText}});
  render(<RoomCode code="TLFSKBMH"/>);
  await act(async()=>fireEvent.click(screen.getByRole('button',{name:'Copy room code'})));
  expect(writeText).toHaveBeenCalledExactlyOnceWith('TLFSKBMH');expect(screen.getByRole('status')).toHaveTextContent('Copied!');
  await act(()=>vi.advanceTimersByTimeAsync(2000));expect(screen.getByRole('status')).toBeEmptyDOMElement();
});
it('falls back on HTTP or permission rejection and removes its temporary field',async()=>{
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:vi.fn().mockRejectedValue(new Error('Denied'))}});
  const copy=vi.fn(()=>true);Object.defineProperty(document,'execCommand',{configurable:true,value:copy});
  render(<RoomCode code="TLFSKBMH"/>);const button=screen.getByRole('button');button.focus();fireEvent.click(button);
  await waitFor(()=>expect(screen.getByRole('status')).toHaveTextContent('Copied!'));
  expect(copy).toHaveBeenCalledWith('copy');expect(document.querySelector('textarea')).toBeNull();expect(button).toHaveFocus();
});
it('keeps the code selectable and explains a failed fallback',async()=>{
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:undefined});
  Object.defineProperty(document,'execCommand',{configurable:true,value:()=>false});
  render(<RoomCode code="TLFSKBMH"/>);fireEvent.click(screen.getByRole('button'));
  await waitFor(()=>expect(screen.getByRole('status')).toHaveTextContent('Select the room code'));
  expect(screen.getByText('TLFSKBMH',{exact:true})).toBeInTheDocument();expect(document.querySelector('textarea')).toBeNull();
});
