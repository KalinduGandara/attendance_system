import { Card, Group, Stack, Text, Title } from '@mantine/core';
import { useAuthStore } from '../../lib/authStore';

export function DashboardPage() {
  const user = useAuthStore((s) => s.user);

  return (
    <Stack>
      <Title order={2}>Dashboard</Title>
      <Text c="dimmed">
        Welcome{user?.displayName ? `, ${user.displayName}` : ''}. This is the Phase 0 shell —
        feature modules will land in subsequent phases.
      </Text>

      <Group>
        <Card withBorder w={280}>
          <Text fw={600}>Roles</Text>
          <Text size="sm" c="dimmed">
            {user?.roles.join(', ') || '—'}
          </Text>
        </Card>
        <Card withBorder w={280}>
          <Text fw={600}>Permissions</Text>
          <Text size="sm" c="dimmed">
            {user?.permissions.length ?? 0} granted
          </Text>
        </Card>
      </Group>
    </Stack>
  );
}
