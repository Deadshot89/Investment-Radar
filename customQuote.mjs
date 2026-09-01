import { app } from '@azure/functions';
import { resolveCustomAssetQuote } from '../lib/customAsset.mjs';

app.http('customQuote', {
  methods: ['GET'],
  authLevel: 'anonymous',
  route: 'custom-quote',
  handler: async (request, context) => {
    try {
      const params = Object.fromEntries(new URL(request.url).searchParams.entries());
      const item = await resolveCustomAssetQuote(params);
      return {
        status: 200,
        jsonBody: item,
        headers: { 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' }
      };
    } catch (error) {
      context.error(error);
      return { status: 400, jsonBody: { error: error instanceof Error ? error.message : 'Custom quote error' } };
    }
  }
});
