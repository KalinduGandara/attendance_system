import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { appTheme } from '../theme/theme';
import { refreshAccessToken } from '../lib/apiClient';
import { useAuthStore } from '../lib/authStore';
import { ProtectedRoute } from './ProtectedRoute';
import { AppShellLayout } from './AppShellLayout';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ForbiddenPage } from '../features/errors/ForbiddenPage';
import { NotFoundPage } from '../features/errors/NotFoundPage';
import { UsersPage } from '../features/identity/pages/UsersPage';
import { EmployeesListPage } from '../features/organization/pages/EmployeesListPage';
import { EmployeeFormPage } from '../features/organization/pages/EmployeeFormPage';
import { DepartmentsPage } from '../features/organization/pages/DepartmentsPage';
import { GroupsPage } from '../features/organization/pages/GroupsPage';
import { CustomFieldsPage } from '../features/organization/pages/CustomFieldsPage';
import { HolidaysPage } from '../features/organization/pages/HolidaysPage';

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, refetchOnWindowFocus: false } }
});

export function App() {
  const [ready, setReady] = useState(false);
  const markBootstrapped = useAuthStore((s) => s.markBootstrapped);

  useEffect(() => {
    refreshAccessToken().finally(() => {
      markBootstrapped();
      setReady(true);
    });
  }, [markBootstrapped]);

  return (
    <MantineProvider theme={appTheme} defaultColorScheme="light">
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          {ready && (
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/forbidden" element={<ForbiddenPage />} />
              <Route
                element={
                  <ProtectedRoute>
                    <AppShellLayout />
                  </ProtectedRoute>
                }
              >
                <Route path="/" element={<DashboardPage />} />
                <Route
                  path="/users"
                  element={
                    <ProtectedRoute requirePermission="user.read">
                      <UsersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/employees"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <EmployeesListPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/employees/new"
                  element={
                    <ProtectedRoute requirePermission="employee.write">
                      <EmployeeFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/employees/:id"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <EmployeeFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/departments"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <DepartmentsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/groups"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <GroupsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/custom-fields"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <CustomFieldsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/holidays"
                  element={
                    <ProtectedRoute requirePermission="employee.read">
                      <HolidaysPage />
                    </ProtectedRoute>
                  }
                />
              </Route>
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          )}
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  );
}
