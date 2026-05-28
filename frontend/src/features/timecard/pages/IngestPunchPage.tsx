import { useState } from 'react';
import {
  Alert,
  Button,
  Code,
  Group,
  Paper,
  Select,
  Stack,
  Text,
  TextInput,
  Textarea,
  Title
} from '@mantine/core';
import { useMutation } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { timecardApi } from '../api';
import { describeApiError } from '../../../lib/apiError';
import type {
  CredentialType,
  IngestionResponse,
  PunchBatchRequest,
  PunchEventType
} from '../types';

const DEFAULT_PAYLOAD = JSON.stringify(
  {
    externalEventId: 'evt-' + Date.now(),
    eventType: 'CHECK_IN' as PunchEventType,
    eventTime: new Date().toISOString()
  },
  null,
  2
);

/**
 * Admin/dev-only page for submitting punches against the live ingestion endpoint.
 * Handy for end-to-end verification of the Phase 5 acceptance criterion in dev.
 */
export function IngestPunchPage() {
  const [sourceId, setSourceId] = useState('');
  const [credentialType, setCredentialType] = useState<CredentialType>('RFID');
  const [credentialValue, setCredentialValue] = useState('');
  const [payload, setPayload] = useState(DEFAULT_PAYLOAD);
  const [response, setResponse] = useState<IngestionResponse | null>(null);

  const mutation = useMutation({
    mutationFn: (body: PunchBatchRequest) => timecardApi.ingest(body, crypto.randomUUID()),
    onSuccess: (data) => {
      setResponse(data);
      notifications.show({
        message: `Accepted ${data.accepted}, duplicate ${data.duplicate}, unresolved ${data.unresolved}, invalid ${data.invalid}`,
        color: 'green'
      });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function submit() {
    if (!sourceId) {
      notifications.show({ message: 'sourceId is required', color: 'red' });
      return;
    }
    let parsed;
    try {
      parsed = JSON.parse(payload);
    } catch {
      notifications.show({ message: 'Payload is not valid JSON', color: 'red' });
      return;
    }
    const body: PunchBatchRequest = {
      sourceId,
      events: [
        {
          ...parsed,
          credential:
            credentialValue && credentialType
              ? { type: credentialType, value: credentialValue }
              : undefined
        }
      ]
    };
    mutation.mutate(body);
  }

  return (
    <Stack>
      <Title order={2}>Ingest punch (debug)</Title>
      <Text c="dimmed" size="sm">
        Submits a single-event batch against <Code>POST /api/v1/ingestion/punches</Code>. Mirrors the
        REST adapter the future device SDK will speak.
      </Text>

      <Paper withBorder p="md">
        <Stack>
          <TextInput
            label="Source ID"
            placeholder="UUID of the ingestion source"
            value={sourceId}
            onChange={(e) => setSourceId(e.currentTarget.value)}
          />
          <Group grow>
            <Select
              label="Credential type"
              data={['RFID', 'QR', 'MOBILE', 'FACE', 'FINGER', 'PIN']}
              value={credentialType}
              onChange={(v) => setCredentialType((v as CredentialType) ?? 'RFID')}
            />
            <TextInput
              label="Credential value"
              placeholder="e.g. RFID-001"
              value={credentialValue}
              onChange={(e) => setCredentialValue(e.currentTarget.value)}
            />
          </Group>
          <Textarea
            label="Event payload"
            value={payload}
            onChange={(e) => setPayload(e.currentTarget.value)}
            minRows={8}
            autosize
            ff="monospace"
          />
          <Group justify="flex-end">
            <Button onClick={submit} loading={mutation.isPending}>
              Send
            </Button>
          </Group>
        </Stack>
      </Paper>

      {response && (
        <Alert color="green" title="Last response">
          <Code block>{JSON.stringify(response, null, 2)}</Code>
        </Alert>
      )}
    </Stack>
  );
}
