export interface AvatarConfig {version:1;body:number;eyes:number;iris:number;wings:number;marking:number;accessory:number;expression:number;species?:number}
export const birdSpecies=['Moon owl','Barn owl','Snowy owl','Little owl','Horned owl','Tawny owl','Nightjar','Raven'] as const;
export const avatarBounds={body:8,eyes:3,iris:5,wings:2,marking:3,accessory:7,expression:5} as const;
export function readAvatar(value:unknown):AvatarConfig|undefined{
  if(!value||typeof value!=='object'||Array.isArray(value))return;
  const record=value as Record<string,unknown>;if(record.version!==1)return;
  if(record.species!==undefined&&(!Number.isInteger(record.species)||Number(record.species)<0||Number(record.species)>7))return;
  for(const [key,max] of Object.entries(avatarBounds))if(!Number.isInteger(record[key])||Number(record[key])<0||Number(record[key])>max)return;
  return {version:1,body:Number(record.body),eyes:Number(record.eyes),iris:Number(record.iris),wings:Number(record.wings),marking:Number(record.marking),accessory:Number(record.accessory),expression:Number(record.expression),...(record.species===undefined?{}:{species:Number(record.species)})};
}
/** Same String.hashCode/xorshift32 sequence as the server's legacy fallback. */
export function avatarFor(identity:string):AvatarConfig{
  let state=0;for(let i=0;i<identity.length;i++)state=(Math.imul(state,31)+identity.charCodeAt(i))|0;if(!state)state=0x6d2b79f5;
  const next=(bound:number)=>{state^=state<<13;state^=state>>>17;state^=state<<5;return (state>>>0)%bound;};
  return {version:1,body:next(9),eyes:next(4),iris:next(6),wings:next(3),marking:next(4),accessory:next(8),expression:next(6)};
}
export function surpriseAvatar(current:AvatarConfig):AvatarConfig{
  const values=crypto.getRandomValues(new Uint32Array(8));const keys=Object.keys(avatarBounds) as (keyof typeof avatarBounds)[];
  const result={version:1} as AvatarConfig;keys.forEach((key,i)=>{result[key]=values[i]%(avatarBounds[key]+1);});
  result.species=values[7]%birdSpecies.length;
  if(JSON.stringify(result)===JSON.stringify(current))result.accessory=(result.accessory+1)%8;
  return result;
}
