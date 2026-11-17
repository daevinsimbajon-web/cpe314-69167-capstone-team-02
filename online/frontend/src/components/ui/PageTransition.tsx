import type { ReactNode } from 'react';
import { motion } from 'motion/react';
import { useQuietMotion } from './MotionProvider';
export function PageTransition({children}:{children:ReactNode}) {
  const quiet=useQuietMotion();
  return <motion.div className="page-transition" initial={quiet?false:{opacity:0,y:8}} animate={{opacity:1,y:0}}>{children}</motion.div>;
}
