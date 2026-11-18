import type { SVGProps } from 'react';

/** Original Quiz Night Host artwork. Keep the export SVGs in assets/brand in sync. */
export function OwlArtwork(props: SVGProps<SVGSVGElement>) {
  return <svg viewBox="0 0 240 240" fill="none" {...props}>
    <ellipse cx="120" cy="222" rx="62" ry="8" fill="currentColor" opacity=".08"/>
    <g className="owl-body">
      <path d="M64 71 54 27q33 4 48 21h36q15-17 48-21l-10 44q28 27 28 74c0 46-32 74-84 74s-84-28-84-74q0-47 28-74Z" fill="#6D47CB"/>
      <path d="M64 71 59 39l33 22M176 71l5-32-33 22" stroke="#A78BFA" strokeWidth="6" strokeLinecap="round"/>
      <path d="M120 94c-23-35-70-31-70 13 0 37 35 54 70 77 35-23 70-40 70-77 0-44-47-48-70-13Z" fill="#DDD4FF"/>
      <path d="M120 94v90c35-23 70-40 70-77 0-44-47-48-70-13Z" fill="#C4B5FD"/>
      <g className="owl-wing owl-wing-left"><path d="M46 123c-22 12-23 48 15 69 3-26 4-45-15-69Z" fill="#45328C"/><path d="m44 149 7 20" stroke="#8B5CF6" strokeWidth="4" strokeLinecap="round"/></g>
      <g className="owl-wing owl-wing-right"><path d="M194 123c22 12 23 48-15 69-3-26-4-45 15-69Z" fill="#45328C"/><path d="m196 149-7 20" stroke="#8B5CF6" strokeWidth="4" strokeLinecap="round"/></g>
      <g className="owl-eyes">
        <circle cx="86" cy="112" r="29" fill="#FBF9FF"/><circle cx="154" cy="112" r="29" fill="#FBF9FF"/>
        <circle cx="88" cy="113" r="18" fill="#171D39"/><circle cx="152" cy="113" r="18" fill="#171D39"/>
        <circle cx="88" cy="113" r="13" stroke="#8B5CF6" strokeWidth="3"/><circle cx="152" cy="113" r="13" stroke="#8B5CF6" strokeWidth="3"/>
        <circle cx="93" cy="106" r="5" fill="white"/><circle cx="157" cy="106" r="5" fill="white"/>
        <circle cx="82" cy="119" r="2" fill="#22D3EE"/><circle cx="146" cy="119" r="2" fill="#22D3EE"/>
      </g>
      <path d="m111 141 9 13 9-13q-9-7-18 0Z" fill="#FBBF24"/>
      <path className="owl-smile" d="M108 164q12 12 24 0" stroke="#6D47CB" strokeWidth="4" strokeLinecap="round"/>
      <path d="m110 189 10 8 10-8" stroke="#A78BFA" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="m87 213-7 9m16-8v10m57-11 7 9m-16-8v10" stroke="#FBBF24" strokeWidth="6" strokeLinecap="round"/>
    </g>
  </svg>;
}
