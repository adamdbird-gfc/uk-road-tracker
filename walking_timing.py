"""Coordinate-free ASGI timings. Send duration is server backpressure, not wire RTT."""
import logging
import time
from starlette.responses import JSONResponse

logger = logging.getLogger('roadprints.walking_timing')


class WalkingJSONResponse(JSONResponse):
    def render(self, content):
        started = time.perf_counter()
        body = super().render(content)
        logger.info('walking_json serialize_ms=%.2f response_bytes=%s',
                    (time.perf_counter()-started)*1000, len(body))
        return body


class WalkingTimingMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http' or scope.get('path') != '/match-walking':
            return await self.app(scope, receive, send)
        started = time.perf_counter()
        read_ms = send_ms = 0.0
        request_bytes = response_bytes = 0
        headers_ms = None
        async def timed_receive():
            nonlocal read_ms, request_bytes
            stage = time.perf_counter()
            message = await receive()
            read_ms += (time.perf_counter()-stage)*1000
            request_bytes += len(message.get('body', b''))
            return message
        async def timed_send(message):
            nonlocal send_ms, response_bytes, headers_ms
            if message['type'] == 'http.response.start':
                headers_ms = (time.perf_counter()-started)*1000
                message = dict(message)
                message['headers'] = list(message.get('headers', [])) + [
                    (b'server-timing', f'request;dur={headers_ms:.2f}, receive;dur={read_ms:.2f}'.encode())]
            response_bytes += len(message.get('body', b''))
            stage = time.perf_counter()
            await send(message)
            send_ms += (time.perf_counter()-stage)*1000
        try:
            await self.app(scope, timed_receive, timed_send)
        finally:
            logger.info('walking_http request_bytes=%s response_bytes=%s receive_ms=%.2f headers_ms=%s send_ms=%.2f total_ms=%.2f',
                        request_bytes, response_bytes, read_ms, headers_ms, send_ms,
                        (time.perf_counter()-started)*1000)
