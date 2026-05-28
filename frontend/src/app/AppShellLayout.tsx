import { AppShell, Burger, Group, NavLink, ScrollArea, Text, Avatar, Menu, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconBuildingSkyscraper,
  IconCalendar,
  IconCalendarEvent,
  IconCalendarStats,
  IconCalendarTime,
  IconClockHour4,
  IconCloudUpload,
  IconDeviceMobile,
  IconFingerprint,
  IconLayoutDashboard,
  IconListDetails,
  IconLogout,
  IconScale,
  IconShieldLock,
  IconStopwatch,
  IconUpload,
  IconUserCircle,
  IconUsers,
  IconUsersGroup
} from '@tabler/icons-react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../lib/authStore';
import { authApi } from '../lib/authApi';

interface NavItem {
  label: string;
  to: string;
  icon: typeof IconLayoutDashboard;
  permission?: string;
}

const NAV: NavItem[] = [
  { label: 'Dashboard', to: '/', icon: IconLayoutDashboard },
  { label: 'Employees', to: '/employees', icon: IconUsers, permission: 'employee.read' },
  { label: 'Departments', to: '/departments', icon: IconBuildingSkyscraper, permission: 'employee.read' },
  { label: 'Groups', to: '/groups', icon: IconUsersGroup, permission: 'employee.read' },
  { label: 'Custom fields', to: '/custom-fields', icon: IconListDetails, permission: 'employee.read' },
  { label: 'Holidays', to: '/holidays', icon: IconCalendarStats, permission: 'employee.read' },
  { label: 'Devices', to: '/devices', icon: IconDeviceMobile, permission: 'device.read' },
  { label: 'Ingestion sources', to: '/ingestion-sources', icon: IconCloudUpload, permission: 'device.read' },
  { label: 'Time codes', to: '/time-codes', icon: IconScale, permission: 'timecode.read' },
  { label: 'Shifts', to: '/shifts', icon: IconClockHour4, permission: 'shift.read' },
  { label: 'Schedule templates', to: '/schedule-templates', icon: IconCalendar, permission: 'schedule.read' },
  { label: 'Assignments', to: '/schedule-assignments', icon: IconCalendarEvent, permission: 'schedule.read' },
  { label: 'Temporary schedules', to: '/temporary-schedules', icon: IconCalendarTime, permission: 'schedule.read' },
  { label: 'Time cards', to: '/timecards', icon: IconStopwatch, permission: 'timecard.read' },
  { label: 'Punches', to: '/punches', icon: IconFingerprint, permission: 'timecard.read' },
  { label: 'Unresolved punches', to: '/punches/unresolved', icon: IconAlertTriangle, permission: 'timecard.read' },
  { label: 'Ingest punch', to: '/ingest', icon: IconUpload, permission: 'ingestion.write' },
  { label: 'Users', to: '/users', icon: IconShieldLock, permission: 'user.read' }
];

export function AppShellLayout() {
  const [opened, { toggle }] = useDisclosure();
  const user = useAuthStore((s) => s.user);
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();
  const location = useLocation();

  const items = NAV.filter((i) => !i.permission || hasPermission(i.permission));

  async function onLogout() {
    try {
      await authApi.logout();
    } catch {
      // proceed regardless
    }
    clear();
    navigate('/login', { replace: true });
  }

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 260, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={600}>Attendance</Text>
          </Group>
          <Menu position="bottom-end" withArrow>
            <Menu.Target>
              <UnstyledButton>
                <Group gap="xs">
                  <Avatar size="sm" radius="xl" color="blue">
                    {(user?.displayName ?? user?.username ?? '?').slice(0, 1).toUpperCase()}
                  </Avatar>
                  <Text size="sm" fw={500} visibleFrom="sm">
                    {user?.displayName ?? user?.username}
                  </Text>
                </Group>
              </UnstyledButton>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item leftSection={<IconUserCircle size={14} />} disabled>
                {user?.roles.join(', ')}
              </Menu.Item>
              <Menu.Divider />
              <Menu.Item color="red" leftSection={<IconLogout size={14} />} onClick={onLogout}>
                Sign out
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <AppShell.Section grow component={ScrollArea}>
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                label={item.label}
                leftSection={<Icon size={16} />}
                active={location.pathname === item.to}
                onClick={() => navigate(item.to)}
              />
            );
          })}
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
