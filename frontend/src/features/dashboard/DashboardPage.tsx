import { Card, Group, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../lib/authStore';

export function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const { t } = useTranslation();

  return (
    <Stack>
      <Title order={2}>{t('dashboard.title')}</Title>
      <Text c="dimmed">
        {user?.displayName
          ? t('dashboard.welcome', { name: user.displayName })
          : t('dashboard.welcomeNoName')}{' '}
        {t('dashboard.subtitle')}
      </Text>

      <Group>
        <Card withBorder w={280}>
          <Text fw={600}>{t('dashboard.roles')}</Text>
          <Text size="sm" c="dimmed">
            {user?.roles.join(', ') || t('common.dash')}
          </Text>
        </Card>
        <Card withBorder w={280}>
          <Text fw={600}>{t('dashboard.permissions')}</Text>
          <Text size="sm" c="dimmed">
            {t('dashboard.permissionsGranted', { count: user?.permissions.length ?? 0 })}
          </Text>
        </Card>
      </Group>
    </Stack>
  );
}
