import { motion, type HTMLMotionProps } from 'motion/react';
import { useQuietMotion } from './MotionProvider';
export function AnimatedButton({className='',...props}:HTMLMotionProps<'button'>) {
  const quiet=useQuietMotion();
  return <motion.button whileHover={quiet?undefined:{y:-2}} whileTap={quiet?undefined:{scale:.98}} className={`button ${className}`} {...props}/>;
}
