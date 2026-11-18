import { motion, type HTMLMotionProps } from 'motion/react';
import { useQuietMotion } from './MotionProvider';
export function AnimatedCard({className='',delay=0,...props}:HTMLMotionProps<'section'>&{delay?:number}) {
  const quiet=useQuietMotion();
  return <motion.section initial={quiet?false:{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:quiet?0:delay,duration:quiet?0:.35}} className={`panel ${className}`} {...props}/>;
}
