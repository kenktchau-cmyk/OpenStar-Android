"""Fail if any ELF library inside an APK/wheel cannot map with 16 KB pages.

Also opens Chaquopy's nested .imy ZIP archives, where NumPy dependencies live.
Usage: python tools/check_native_alignment.py path/to/app.apk
"""
import argparse
import io
from pathlib import Path
import struct
import zipfile


def check_elf(data):
    if not data.startswith(b"\x7fELF"):
        return None
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        offset = struct.unpack_from(endian + "Q", data, 32)[0]
        size, count = struct.unpack_from(endian + "HH", data, 54)
        fmt = endian + "IIQQQQQQ"
        def values(p): return p[0], p[2], p[3], p[7]
    else:
        offset = struct.unpack_from(endian + "I", data, 28)[0]
        size, count = struct.unpack_from(endian + "HH", data, 42)
        fmt = endian + "IIIIIIII"
        def values(p): return p[0], p[1], p[2], p[7]
    errors = []
    for i in range(count):
        kind, file_offset, address, alignment = values(struct.unpack_from(fmt, data, offset+i*size))
        if kind != 1:  # PT_LOAD
            continue
        if alignment < 16384 or file_offset % 16384 != address % 16384:
            errors.append(f"LOAD {i}: alignment={alignment}, offset={file_offset}, address={address}")
    return errors


def scan_archive(data, prefix):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            if name.endswith((".imy", ".zip", ".whl")):
                yield from scan_archive(archive.read(name), prefix+"!"+name)
            elif ".so" in name:
                errors = check_elf(archive.read(name))
                if errors is not None:
                    yield prefix+"!"+name, errors


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact",type=Path)
    args=parser.parse_args()
    results=list(scan_archive(args.artifact.read_bytes(),args.artifact.name))
    failures=[(path, errors) for path, errors in results if errors]
    for path,errors in failures[:20]:
        print("FAIL",path,errors[0])
    print(f"Checked {len(results)} native libraries; {len(failures)} incompatible with 16 KB pages.")
    if not results:
        raise SystemExit("No ELF libraries were checked: unsupported archive layout.")
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__": main()
