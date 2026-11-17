import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuietMotion } from '../ui/MotionProvider';

/** Let the approved SVG finish a short success gesture after backend confirmation. */
export function useConfirmedNavigation(destination: string | null, replace = false, celebrationMs=1150) {
  const navigate = useNavigate(), quiet = useQuietMotion();
  useEffect(() => {
    if (!destination) return;
    const timer = setTimeout(() => navigate(destination, { replace }),
      quiet ? 0 : celebrationMs);
    return () => clearTimeout(timer);
  }, [destination, navigate, quiet, replace, celebrationMs]);
}
