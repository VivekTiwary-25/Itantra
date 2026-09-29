"""Read the first N rows of a google/fleurs `parquet-data` file without downloading the whole file.

Why: each FLEURS parquet file is one row group whose `audio.bytes` column is dictionary-encoded, so all
audio lives in ONE snappy-compressed dictionary page, in first-occurrence (= row) order, and pyarrow
cannot read part of it. The link here is ~50-75 KB/s, so whole files (8+ GB) are not an option.

How: parse the footer (pyarrow, small range reads) -> byte offset of the audio dictionary page ->
download a growing prefix of that page with resumable HTTP Range requests (cached on disk) ->
decode the thrift page header, decode the snappy stream only as far as the prefix goes, split the
PLAIN BYTE_ARRAY values ([u32 length][bytes]) -> first N audio blobs. The small id / transcription
columns are read normally. Self-check: `verify_against_local()` compares with a full pyarrow read.
"""
from __future__ import annotations

import os
import struct
import time
from pathlib import Path

import httpx
import pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
from huggingface_hub.utils import build_hf_headers

REPO_URL = "https://huggingface.co/datasets/google/fleurs/resolve/main/"
META_COLUMNS = ["id", "transcription", "raw_transcription"]


# ---------------------------------------------------------------- thrift compact (page header only)
def _varint(buf: bytes, pos: int) -> tuple[int, int]:
    shift = result = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def _zigzag(n: int) -> int:
    return (n >> 1) ^ -(n & 1)


def _skip_struct(buf: bytes, pos: int, out: dict | None = None) -> int:
    last = 0
    while True:
        byte = buf[pos]
        pos += 1
        if byte == 0:
            return pos
        delta, ftype = byte >> 4, byte & 0x0F
        if delta:
            fid = last + delta
        else:
            raw, pos = _varint(buf, pos)
            fid = _zigzag(raw)
        last = fid
        pos, value = _skip_value(buf, pos, ftype)
        if out is not None:
            out[fid] = value


def _skip_value(buf: bytes, pos: int, ftype: int):
    if ftype in (1, 2):                    # bool true / false (inline)
        return pos, ftype == 1
    if ftype == 3:                         # byte
        return pos + 1, buf[pos]
    if ftype in (4, 5, 6):                 # i16 / i32 / i64
        raw, pos = _varint(buf, pos)
        return pos, _zigzag(raw)
    if ftype == 7:                         # double
        return pos + 8, None
    if ftype == 8:                         # binary
        n, pos = _varint(buf, pos)
        return pos + n, None
    if ftype in (9, 10):                   # list / set
        head = buf[pos]
        pos += 1
        size, etype = head >> 4, head & 0x0F
        if size == 15:
            size, pos = _varint(buf, pos)
        for _ in range(size):
            pos, _ = _skip_value(buf, pos, etype)
        return pos, None
    if ftype == 12:                        # struct
        sub: dict = {}
        return _skip_struct(buf, pos, sub), sub
    raise ValueError(f"unsupported thrift type {ftype}")


def page_header(buf: bytes) -> tuple[dict, int]:
    fields: dict = {}
    end = _skip_struct(buf, 0, fields)
    return fields, end


# ---------------------------------------------------------------- raw snappy, prefix-tolerant
def snappy_prefix(src: bytes, out: bytearray, state: dict) -> None:
    """Decode as many complete snappy elements of `src` as possible, appending to `out`.
    `state['pos']` persists between calls so a growing `src` is decoded incrementally."""
    pos = state.get("pos")
    if pos is None:
        state["total"], pos = _varint(src, 0)
    n = len(src)
    while pos < n:
        tag = src[pos]
        kind = tag & 3
        if kind == 0:
            length = tag >> 2
            hdr = 1
            if length >= 60:
                extra = length - 59
                if pos + 1 + extra > n:
                    break
                length = int.from_bytes(src[pos + 1:pos + 1 + extra], "little")
                hdr += extra
            length += 1
            if pos + hdr + length > n:
                break
            out += src[pos + hdr:pos + hdr + length]
            pos += hdr + length
            continue
        if kind == 1:
            if pos + 2 > n:
                break
            length = ((tag >> 2) & 7) + 4
            offset = ((tag >> 5) << 8) | src[pos + 1]
            pos += 2
        elif kind == 2:
            if pos + 3 > n:
                break
            length = (tag >> 2) + 1
            offset = src[pos + 1] | (src[pos + 2] << 8)
            pos += 3
        else:
            if pos + 5 > n:
                break
            length = (tag >> 2) + 1
            offset = int.from_bytes(src[pos + 1:pos + 5], "little")
            pos += 5
        start = len(out) - offset
        if offset >= length:
            out += out[start:start + length]
        else:
            for i in range(length):
                out.append(out[start + i])
    state["pos"] = pos


def plain_byte_arrays(buf: bytes | bytearray, count: int) -> list[bytes]:
    values, pos = [], 0
    while len(values) < count and pos + 4 <= len(buf):
        (length,) = struct.unpack_from("<I", buf, pos)
        if pos + 4 + length > len(buf):
            break
        values.append(bytes(buf[pos + 4:pos + 4 + length]))
        pos += 4 + length
    return values


# ---------------------------------------------------------------- network
def _fetch_range(url: str, start: int, end: int, cache: Path) -> bytes:
    """Return bytes [start, end) of url; the prefix already fetched is kept in `cache`, resumed on failure."""
    have = cache.read_bytes() if cache.exists() else b""
    headers = build_hf_headers()
    while len(have) < end - start:
        want_from = start + len(have)
        try:
            with httpx.stream("GET", url, headers={**headers, "Range": f"bytes={want_from}-{end - 1}"},
                              follow_redirects=True, timeout=httpx.Timeout(60.0)) as resp:
                resp.raise_for_status()
                with cache.open("ab") as handle:
                    for chunk in resp.iter_bytes(1 << 16):
                        handle.write(chunk)
                        have += chunk
        except (httpx.HTTPError, OSError) as exc:
            print(f"  range retry at {len(have)} B: {type(exc).__name__}", flush=True)
            time.sleep(3)
    return have[:end - start]


def first_rows_remote(config: str, split: str, count: int, cache_dir: Path, step: int = 4 << 20) -> list[dict]:
    rel = f"parquet-data/{config}/{split}-00000-of-00001.parquet"
    fs = HfFileSystem()
    with fs.open("datasets/google/fleurs/" + rel, "rb", block_size=1 << 18) as handle:
        pf = pq.ParquetFile(handle)
        meta = pf.read_row_group(0, columns=META_COLUMNS).to_pylist()[:count]
        chunk = next(pf.metadata.row_group(0).column(i) for i in range(pf.metadata.num_columns)
                     if pf.metadata.row_group(0).column(i).path_in_schema == "audio.bytes")
    if chunk.compression != "SNAPPY" or chunk.dictionary_page_offset is None:
        raise RuntimeError(f"{rel}: unexpected layout {chunk.compression} dict={chunk.dictionary_page_offset}")
    dict_off, chunk_end = chunk.dictionary_page_offset, chunk.data_page_offset
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache = cache_dir / f"{config}-{split}.dictprefix"
    url = REPO_URL + rel
    size = min(step, chunk_end - dict_off)
    while True:
        raw = _fetch_range(url, dict_off, dict_off + size, cache)
        header, hdr_len = page_header(raw)
        if header.get(1) != 2:  # PageType.DICTIONARY_PAGE
            raise RuntimeError(f"{rel}: page at dictionary offset is type {header.get(1)}")
        out, state = bytearray(), {}
        snappy_prefix(raw[hdr_len:hdr_len + header[3]], out, state)
        blobs = plain_byte_arrays(out, count)
        print(f"  {config}/{split}: {size / 1e6:.1f} MB prefix -> {len(blobs)}/{count} clips", flush=True)
        if len(blobs) >= count:
            break
        if size >= chunk_end - dict_off:
            raise RuntimeError(f"{rel}: only {len(blobs)} values in the whole dictionary page")
        size = min(size + step, chunk_end - dict_off)
    return [{**m, "audio": {"bytes": b}} for m, b in zip(meta, blobs)]


def first_rows_local(path: Path, count: int) -> list[dict]:
    table = pq.read_table(path, columns=META_COLUMNS + ["audio"])
    return table.slice(0, count).to_pylist()


def verify_against_local(path: Path, count: int = 5) -> bool:
    """Decode the prefix of a LOCAL file with the same code path and compare with pyarrow."""
    pf = pq.ParquetFile(path)
    chunk = next(pf.metadata.row_group(0).column(i) for i in range(pf.metadata.num_columns)
                 if pf.metadata.row_group(0).column(i).path_in_schema == "audio.bytes")
    with open(path, "rb") as handle:
        handle.seek(chunk.dictionary_page_offset)
        raw = handle.read(16 << 20)
    header, hdr_len = page_header(raw)
    out, state = bytearray(), {}
    snappy_prefix(raw[hdr_len:hdr_len + header[3]], out, state)
    blobs = plain_byte_arrays(out, count)
    truth = [r["audio"]["bytes"] for r in first_rows_local(path, count)]
    return len(blobs) == count and blobs == truth


if __name__ == "__main__":
    import sys
    print(verify_against_local(Path(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 5), flush=True)
    os._exit(0)
