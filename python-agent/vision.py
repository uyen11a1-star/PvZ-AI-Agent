from __future__ import annotations

import base64
import hashlib
import struct


def jpeg_size(data: bytes) -> tuple[int, int]:
    # Parse common JPEG SOF markers without external libraries.
    if len(data) < 4 or data[:2] != b'\xff\xd8':
        raise ValueError('Not a JPEG image')
    i = 2
    sof_markers = set(range(0xC0, 0xC4)) | set(range(0xC5, 0xC8)) | set(range(0xC9, 0xCC)) | set(range(0xCD, 0xD0))
    while i + 3 < len(data):
        while i < len(data) and data[i] != 0xFF:
            i += 1
        while i < len(data) and data[i] == 0xFF:
            i += 1
        if i >= len(data): break
        marker = data[i]
        i += 1
        if marker in (0xD8, 0xD9):
            continue
        if i + 2 > len(data): break
        seg_len = struct.unpack('>H', data[i:i+2])[0]
        if seg_len < 2 or i + seg_len > len(data): break
        if marker in sof_markers:
            h, w = struct.unpack('>HH', data[i+3:i+7])
            return w, h
        i += seg_len
    raise ValueError('Could not find JPEG dimensions')


def image_to_data_url(image_bytes: bytes) -> tuple[str, tuple[int, int]]:
    size = jpeg_size(image_bytes)
    encoded = base64.b64encode(image_bytes).decode('ascii')
    return f'data:image/jpeg;base64,{encoded}', size


def image_fingerprint(image_bytes: bytes) -> str:
    return hashlib.sha256(image_bytes).hexdigest()
