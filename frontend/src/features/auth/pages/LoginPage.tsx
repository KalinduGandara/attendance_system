import { Alert, Button, Card, Center, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../../../lib/authApi';
import { useAuthStore } from '../../../lib/authStore';
import type { ProblemDetail } from '../../../lib/types';
import { AxiosError } from 'axios';

interface LocationState {
  from?: { pathname: string };
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm({
    initialValues: { username: '', password: '' },
    validate: {
      username: (v) => (v.trim().length === 0 ? 'Username is required' : null),
      password: (v) => (v.length === 0 ? 'Password is required' : null)
    }
  });

  if (user) {
    const from = (location.state as LocationState | null)?.from?.pathname ?? '/';
    return <Navigate to={from} replace />;
  }

  async function onSubmit(values: typeof form.values) {
    setSubmitting(true);
    setError(null);
    try {
      const res = await authApi.login(values.username, values.password);
      setSession(res.accessToken, res.user);
      const from = (location.state as LocationState | null)?.from?.pathname ?? '/';
      navigate(from, { replace: true });
    } catch (err) {
      const ax = err as AxiosError<ProblemDetail>;
      setError(ax.response?.data?.detail ?? 'Unable to sign in. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Center mih="100vh" bg="gray.0" px="md">
      <Card withBorder shadow="sm" radius="md" p="xl" w={{ base: '100%', sm: 420 }}>
        <Stack>
          <Title order={2} ta="center">
            Sign in
          </Title>
          {error && (
            <Alert color="red" variant="light">
              {error}
            </Alert>
          )}
          <form onSubmit={form.onSubmit(onSubmit)}>
            <Stack>
              <TextInput
                label="Username"
                autoComplete="username"
                autoFocus
                {...form.getInputProps('username')}
              />
              <PasswordInput
                label="Password"
                autoComplete="current-password"
                {...form.getInputProps('password')}
              />
              <Button type="submit" loading={submitting} fullWidth>
                Sign in
              </Button>
            </Stack>
          </form>
        </Stack>
      </Card>
    </Center>
  );
}
