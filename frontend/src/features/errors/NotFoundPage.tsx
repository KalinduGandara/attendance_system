import { Button, Center, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Center mih="100vh">
      <Stack align="center">
        <Title order={1}>404</Title>
        <Text c="dimmed">The page you requested does not exist.</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          Back to dashboard
        </Button>
      </Stack>
    </Center>
  );
}
