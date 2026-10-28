import unittest
from unittest.mock import patch, AsyncMock
from walking_timing import WalkingTimingMiddleware, WalkingJSONResponse


class WalkingTimingTests(unittest.IsolatedAsyncioTestCase):
    async def test_body_and_headers_are_preserved_and_timing_is_coordinate_free(self):
        messages = []
        async def app(scope, receive, send):
            self.assertEqual(b'private-body', (await receive())['body'])
            await send({'type':'http.response.start','status':200,'headers':[(b'content-type',b'application/json')]})
            await send({'type':'http.response.body','body':b'{"ok":true}'})
        async def receive():
            return {'type':'http.request','body':b'private-body'}
        async def send(message): messages.append(message)
        with self.assertLogs('roadprints.walking_timing',level='INFO') as logs:
            await WalkingTimingMiddleware(app)({'type':'http','path':'/match-walking'},receive,send)
        self.assertEqual(b'{"ok":true}',messages[1]['body'])
        headers=dict(messages[0]['headers'])
        self.assertEqual(b'application/json',headers[b'content-type'])
        self.assertIn(b'request;dur=',headers[b'server-timing'])
        self.assertIn('request_bytes=12',logs.output[0])
        self.assertNotIn('private-body',logs.output[0])

    async def test_json_serialization_preserves_content_and_logs_only_size_and_time(self):
        import json
        with self.assertLogs('roadprints.walking_timing',level='INFO') as logs:
            response=WalkingJSONResponse({'geometry':{'coordinates':[[1.0,51],[2,52]]},'private':'secret'})
        self.assertEqual({'geometry':{'coordinates':[[1.0,51],[2,52]]},'private':'secret'},json.loads(response.body))
        self.assertIn('serialize_ms=',logs.output[0])
        self.assertNotIn('secret',logs.output[0])

    async def test_other_routes_are_not_wrapped(self):
        app=AsyncMock();scope={'type':'http','path':'/match'};receive=AsyncMock();send=AsyncMock()
        await WalkingTimingMiddleware(app)(scope,receive,send)
        app.assert_awaited_once_with(scope,receive,send)
