from __future__ import annotations

import json
import urllib.request


class BridgeError(RuntimeError):
    pass


class AndroidBridge:
    def __init__(self, base_url: str, timeout: float = 12.0) -> None:
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout

    def _request(self, path: str, method: str = 'GET', body: bytes | None = None, content_type: str = 'application/json') -> tuple[bytes, str]:
        req = urllib.request.Request(self.base_url + path, data=body, method=method)
        if body is not None:
            req.add_header('Content-Type', content_type)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as r:
                return r.read(), r.headers.get('Content-Type', '')
        except Exception as exc:
            raise BridgeError(f'{method} {path} failed: {exc}') from exc

    def health(self) -> dict:
        raw, _ = self._request('/health')
        return json.loads(raw.decode())

    def screen_size(self) -> tuple[int, int]:
        raw, _ = self._request('/size')
        obj = json.loads(raw.decode())
        return int(obj['width']), int(obj['height'])

    def screenshot(self) -> bytes:
        raw, content_type = self._request('/screen')
        if 'image/' not in content_type:
            raise BridgeError(f'unexpected screenshot content-type: {content_type}')
        return raw

    def tap(self, x: int, y: int) -> None:
        body = json.dumps({'x': x, 'y': y}).encode()
        self._request('/tap', 'POST', body)

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 250) -> None:
        body = json.dumps({'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2, 'duration_ms': duration_ms}).encode()
        self._request('/swipe', 'POST', body)
