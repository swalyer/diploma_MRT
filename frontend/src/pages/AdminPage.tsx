import { Alert, Card, CardContent, Chip, Stack, Typography } from '@mui/material'

export function AdminPage() {
  return <Stack spacing={2}>
    <Typography variant="h4" fontWeight={700}>Admin console</Typography>
    <Alert severity="warning">Demo-grade admin panel: user/role CRUD not yet implemented in UI. Backend auth and role checks remain source of truth.</Alert>
    <Card><CardContent>
      <Stack direction="row" spacing={1} alignItems="center"><Typography fontWeight={700}>Execution profiles</Typography><Chip size="small" label="mock"/><Chip size="small" color="success" label="real"/></Stack>
      <Typography color="text.secondary">Model registry visibility and health checks should be extended here for production.</Typography>
    </CardContent></Card>
  </Stack>
}
