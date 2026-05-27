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
import { DevicesPage } from '../features/device/pages/DevicesPage';
import { IngestionSourcesPage } from '../features/device/pages/IngestionSourcesPage';
import { TimeCodesPage } from '../features/timecode/pages/TimeCodesPage';
import { ShiftsListPage } from '../features/shift/pages/ShiftsListPage';
import { ShiftFormPage } from '../features/shift/pages/ShiftFormPage';
import { ScheduleTemplatesPage } from '../features/schedule/pages/ScheduleTemplatesPage';
import { ScheduleTemplateFormPage } from '../features/schedule/pages/ScheduleTemplateFormPage';
import { ScheduleAssignmentsPage } from '../features/schedule/pages/ScheduleAssignmentsPage';
import { TemporarySchedulesPage } from '../features/schedule/pages/TemporarySchedulesPage';

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
                <Route
                  path="/devices"
                  element={
                    <ProtectedRoute requirePermission="device.read">
                      <DevicesPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/ingestion-sources"
                  element={
                    <ProtectedRoute requirePermission="device.read">
                      <IngestionSourcesPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/time-codes"
                  element={
                    <ProtectedRoute requirePermission="timecode.read">
                      <TimeCodesPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/shifts"
                  element={
                    <ProtectedRoute requirePermission="shift.read">
                      <ShiftsListPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/shifts/new"
                  element={
                    <ProtectedRoute requirePermission="shift.write">
                      <ShiftFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/shifts/:id"
                  element={
                    <ProtectedRoute requirePermission="shift.read">
                      <ShiftFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/schedule-templates"
                  element={
                    <ProtectedRoute requirePermission="schedule.read">
                      <ScheduleTemplatesPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/schedule-templates/new"
                  element={
                    <ProtectedRoute requirePermission="schedule.write">
                      <ScheduleTemplateFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/schedule-templates/:id"
                  element={
                    <ProtectedRoute requirePermission="schedule.read">
                      <ScheduleTemplateFormPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/schedule-assignments"
                  element={
                    <ProtectedRoute requirePermission="schedule.read">
                      <ScheduleAssignmentsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/temporary-schedules"
                  element={
                    <ProtectedRoute requirePermission="schedule.read">
                      <TemporarySchedulesPage />
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
