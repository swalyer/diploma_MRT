import { Alert, Card, CardContent, Chip, Grid2, List, ListItem, ListItemText, Stack, Typography } from '@mui/material'

export function AdminPage() {
  return <Stack spacing={2}>
    <Typography variant="h4">Admin & capability center</Typography>
    <Alert severity="warning">This UI is operational but partial: user CRUD and dynamic config mutation APIs are not currently exposed in frontend.</Alert>

    <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Execution profiles</Typography><Stack direction="row" spacing={1}><Chip label="mock"/><Chip color="success" label="real"/></Stack><Typography variant="body2" color="text.secondary">Defaults and profile switching are backend-configured.</Typography></CardContent></Card></Grid2>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Model capability snapshot</Typography><List dense><ListItem><ListItemText primary="CT liver segmentation" secondary="Implemented in backend/ML service" /></ListItem><ListItem><ListItemText primary="CT lesion segmentation" secondary="Depends on configured nnUNet weights" /></ListItem><ListItem><ListItemText primary="MRI lesion analysis" secondary="Experimental / may be unavailable" /></ListItem></List></CardContent></Card></Grid2>
    </Grid2>
  </Stack>
}
