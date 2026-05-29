import { AppShell, Burger, Group, NavLink, ScrollArea, Text, Avatar, Menu, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconBeach,
  IconBuildingSkyscraper,
  IconChecks,
  IconCalendar,
  IconCalendarEvent,
  IconCalendarStats,
  IconCalendarTime,
  IconClockHour4,
  IconCloudUpload,
  IconDatabaseExport,
  IconDeviceMobile,
  IconFingerprint,
  IconHistory,
  IconLayoutDashboard,
  IconListDetails,
  IconLogout,
  IconReportAnalytics,
  IconScale,
  IconSettings,
  IconShieldLock,
  IconStopwatch,
  IconTrashX,
  IconUpload,
  IconUserCircle,
  IconUsers,
  IconUsersGroup
} from '@tabler/icons-react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../lib/authStore';
import { authApi } from '../lib/authApi';
import './skip-link.css';

interface NavItem {
  labelKey: string;
  to: string;
  icon: typeof IconLayoutDashboard;
  permission?: string;
}

const NAV: NavItem[] = [
  { labelKey: 'nav.dashboard', to: '/', icon: IconLayoutDashboard },
  { labelKey: 'nav.employees', to: '/employees', icon: IconUsers, permission: 'employee.read' },
  { labelKey: 'nav.departments', to: '/departments', icon: IconBuildingSkyscraper, permission: 'employee.read' },
  { labelKey: 'nav.groups', to: '/groups', icon: IconUsersGroup, permission: 'employee.read' },
  { labelKey: 'nav.customFields', to: '/custom-fields', icon: IconListDetails, permission: 'employee.read' },
  { labelKey: 'nav.holidays', to: '/holidays', icon: IconCalendarStats, permission: 'employee.read' },
  { labelKey: 'nav.devices', to: '/devices', icon: IconDeviceMobile, permission: 'device.read' },
  { labelKey: 'nav.ingestionSources', to: '/ingestion-sources', icon: IconCloudUpload, permission: 'device.read' },
  { labelKey: 'nav.timeCodes', to: '/time-codes', icon: IconScale, permission: 'timecode.read' },
  { labelKey: 'nav.shifts', to: '/shifts', icon: IconClockHour4, permission: 'shift.read' },
  { labelKey: 'nav.scheduleTemplates', to: '/schedule-templates', icon: IconCalendar, permission: 'schedule.read' },
  { labelKey: 'nav.assignments', to: '/schedule-assignments', icon: IconCalendarEvent, permission: 'schedule.read' },
  { labelKey: 'nav.temporarySchedules', to: '/temporary-schedules', icon: IconCalendarTime, permission: 'schedule.read' },
  { labelKey: 'nav.timeCards', to: '/timecards', icon: IconStopwatch, permission: 'timecard.read' },
  { labelKey: 'nav.punches', to: '/punches', icon: IconFingerprint, permission: 'timecard.read' },
  { labelKey: 'nav.unresolvedPunches', to: '/punches/unresolved', icon: IconAlertTriangle, permission: 'timecard.read' },
  { labelKey: 'nav.ingestPunch', to: '/ingest', icon: IconUpload, permission: 'ingestion.write' },
  { labelKey: 'nav.leaveTypes', to: '/leave-types', icon: IconBeach, permission: 'leave.read' },
  { labelKey: 'nav.leaveRequests', to: '/leave-requests', icon: IconBeach, permission: 'leave.read' },
  { labelKey: 'nav.leaveApprovals', to: '/leave-approvals', icon: IconChecks, permission: 'leave.approve' },
  { labelKey: 'nav.exceptions', to: '/exceptions', icon: IconAlertCircle, permission: 'exception.read' },
  { labelKey: 'nav.reports', to: '/reports', icon: IconReportAnalytics, permission: 'report.run' },
  { labelKey: 'nav.users', to: '/users', icon: IconShieldLock, permission: 'user.read' },
  { labelKey: 'nav.auditLog', to: '/audit-log', icon: IconHistory, permission: 'audit.read' },
  { labelKey: 'nav.backups', to: '/system/backups', icon: IconDatabaseExport, permission: 'system.admin' },
  { labelKey: 'nav.retention', to: '/system/retention', icon: IconTrashX, permission: 'system.admin' },
  { labelKey: 'nav.systemSettings', to: '/system/settings', icon: IconSettings, permission: 'system.admin' }
];

export function AppShellLayout() {
  const [opened, { toggle }] = useDisclosure();
  const { t } = useTranslation();
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
      <a href="#main-content" className="skip-link">
        {t('nav.skipToContent')}
      </a>
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <Burger
              opened={opened}
              onClick={toggle}
              hiddenFrom="sm"
              size="sm"
              aria-label={t('nav.primary')}
            />
            <Text fw={600}>{t('app.name')}</Text>
          </Group>
          <Menu position="bottom-end" withArrow>
            <Menu.Target>
              <UnstyledButton aria-label={user?.displayName ?? user?.username}>
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
                {t('common.actions.signOut')}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm" component="nav" aria-label={t('nav.primary')}>
        <AppShell.Section grow component={ScrollArea}>
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                label={t(item.labelKey)}
                leftSection={<Icon size={16} />}
                active={location.pathname === item.to}
                onClick={() => navigate(item.to)}
              />
            );
          })}
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main id="main-content" component="main">
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
