import { useEffect, useRef, useState } from 'react';
import type { Room } from '../../api';
import type { MascotEvent, MascotEventName } from '../../config/mascotScenes';

export type RoomReactions = Partial<Record<'lobby' | 'gameplay' | 'leaderboard' | 'results', MascotEventName>>;

/** Compare confirmed room snapshots, never local answer selections or presence timers. */
export function roomReactions(previous: Room | null, room: Room, userId?:string): RoomReactions {
  const before = previous?.code === room.code ? previous : null;
  const reactions: RoomReactions = {};
  if (room.status === 'LOBBY' && before?.status === 'LOBBY') {
    const known = new Set(before.participants.filter(p => !p.left).map(p => p.id));
    if (room.participants.some(p => !p.left && !known.has(p.id))) reactions.lobby = 'playerJoined';
  }
  if (before?.status === 'LOBBY' && room.status === 'QUESTION') reactions.gameplay = 'gameStarted';
  if(room.status==='QUESTION'&&room.lastAttempt&&room.lastAttempt.submissionId!==before?.lastAttempt?.submissionId){
    reactions.gameplay=room.lastAttempt.outcome==='CORRECT'?'correct':'incorrect';
  }
  const newReveal = room.status === 'REVEAL' && room.question?.correctAnswer != null
    && (before?.status !== 'REVEAL' || before.question?.number !== room.question.number);
  if (newReveal) {
    reactions.gameplay = room.submittedIndex === null ? 'timeout'
      : room.question!.choices[room.submittedIndex] === room.question!.correctAnswer ? 'correct' : 'incorrect';
    reactions.leaderboard = 'rankingRevealed';
  } else if (before && ['QUESTION', 'REVEAL'].includes(room.status)) {
    const oldRanks = new Map(before.leaderboard.map(p => [p.id, p.rank]));
    if (room.leaderboard.some(p => oldRanks.has(p.id) && oldRanks.get(p.id) !== p.rank)) reactions.leaderboard = 'rankingChanged';
  }
  if (room.status === 'FINISHED' && before && before.status !== 'FINISHED') {
    const ranking=userId?room.leaderboard.find(player=>player.id===userId):undefined;
    reactions.results=ranking?(ranking.rank===1?'won':'lost'):'finished';
    if(room.leaderboard.length)reactions.leaderboard='rankingRevealed';
  }
  return reactions;
}

export function useRoomMascotEvents(room: Room | null, code?: string, userId?:string) {
  const previous = useRef<Room | null>(null), sequence = useRef(0);
  const [events, setEvents] = useState<Partial<Record<keyof RoomReactions, MascotEvent>>>({});
  useEffect(() => { previous.current = null; setEvents({}); }, [code]);
  useEffect(() => {
    if (!room) return;
    const before = previous.current;
    const reactions = roomReactions(before, room, userId);
    previous.current = room;
    const newQuestion = room.status === 'QUESTION' && (before?.status !== 'QUESTION' || before.question?.number !== room.question?.number);
    if (!Object.keys(reactions).length && !newQuestion) return;
    const next: Partial<Record<keyof RoomReactions, MascotEvent>> = {};
    for (const key of Object.keys(reactions) as (keyof RoomReactions)[]) {
      next[key] = { id: `${room.code}:${++sequence.current}:${reactions[key]}`, type: reactions[key]! };
    }
    setEvents(current => ({ ...current, ...(newQuestion ? { gameplay: undefined } : {}), ...next }));
  }, [room,userId]);
  return events;
}
