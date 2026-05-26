import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../lib/authStore';

interface Props {
  children: ReactNode;
  requirePermission?: string;
}

export function ProtectedRoute({ children, requirePermission }: Props) {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const hasPermission = useAuthStore((s) => s.hasPermission);

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (requirePermission && !hasPermission(requirePermission)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <>{children}</>;
}
