import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { HootlyMascot } from '../brand/HootlyMascot';
import { useQuietMotion } from '../ui/MotionProvider';
import '../../styles/room-ui-fixes.css';

export function RoomRemoved(){
  const quiet=useQuietMotion();
  return <motion.section className="panel room-removed" initial={quiet?false:{opacity:0,y:10}} animate={{opacity:1,y:0}} transition={{duration:quiet?0:.25}}>
    <div className="room-removed-owl" data-quiet={quiet||undefined}><HootlyMascot scene="error" mood="sad" size={150} label="Sad Hootly owl"/></div>
    <div role="status"><h1>You’ve been removed from the room.</h1><p>Cheer up! There are more rooms waiting for you.</p></div>
    <div className="actions"><Link className="button primary" to="/join">Join another room</Link><Link className="button" to="/dashboard">Back to dashboard</Link></div>
  </motion.section>;
}
