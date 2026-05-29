import { Button, Center, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  return (
    <Center mih="100vh">
      <Stack align="center">
        <Title order={1}>{t('errors.notFound.code')}</Title>
        <Text c="dimmed">{t('errors.notFound.message')}</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          {t('common.actions.backToDashboard')}
        </Button>
      </Stack>
    </Center>
  );
}
