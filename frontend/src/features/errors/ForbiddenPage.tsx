import { Button, Center, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';

export function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <Center mih="100vh">
      <Stack align="center">
        <Title order={1}>403</Title>
        <Text c="dimmed">You don&apos;t have permission to access this page.</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          Back to dashboard
        </Button>
      </Stack>
    </Center>
  );
}
