import { app } from '@azure/functions';
import { buildUpdateManifest } from '../lib/updateManifest.mjs';

app.http('appUpdate', {
  methods: ['GET'],
  authLevel: 'anonymous',
  route: 'app-update',
  handler: async () => {
    const manifest = buildUpdateManifest(process.env);
    if (!manifest) {
      return {
        status: 503,
        jsonBody: { error: 'app_update_not_configured' }
      };
    }
    return {
      status: 200,
      headers: { 'Cache-Control': 'no-store' },
      jsonBody: manifest
    };
  }
});
