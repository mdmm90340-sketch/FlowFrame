"""Validate rebuilt WebP ELF files against the pinned upstream FFmpeg AAR.

Uses Python's standard library only; does not execute an Android ELF or rewrite headers.
"""
import argparse
import hashlib
import io
import json
import pathlib
import shutil
import struct
import tarfile
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def elf_info(data):
    if data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected a little-endian ELF64 binary')
    machine = struct.unpack_from('<H', data, 18)[0]
    phoff, shoff = struct.unpack_from('<QQ', data, 32)
    phentsize, phnum, shentsize, shnum, _ = struct.unpack_from('<HHHHH', data, 54)
    segments = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phentsize) for i in range(phnum)]
    sections = [struct.unpack_from('<IIQQQQIIQQ', data, shoff + i * shentsize) for i in range(shnum)]
    loads = [p for p in segments if p[0] == 1]
    relro = [p for p in segments if p[0] == 0x6474E552]
    exports, needed, soname = set(), [], None

    def string(blob, offset):
        return blob[offset:blob.index(b'\0', offset)].decode('utf-8')

    for section in sections:
        if section[1] not in (6, 11):
            continue
        strings = sections[section[6]]
        table = data[strings[4]:strings[4] + strings[5]]
        for offset in range(section[4], section[4] + section[5], section[9]):
            if section[1] == 6:
                tag, value = struct.unpack_from('<qQ', data, offset)
                if tag == 1:
                    needed.append(string(table, value))
                elif tag == 14:
                    soname = string(table, value)
            else:
                name, info, other, index, _, _ = struct.unpack_from('<IBBHQQ', data, offset)
                if index and info >> 4 in (1, 2) and other & 3 in (0, 3):
                    exports.add(string(table, name))
    return {
        'machine': machine, 'soname': soname, 'needed': sorted(needed),
        'loadAlignments': [p[7] for p in loads],
        'loadOffsetsCongruent': all((p[3] - p[2]) % 16384 == 0 for p in loads),
        'relroEndsAligned': bool(relro) and all((p[3] + p[6]) % 16384 == 0 for p in relro),
        'exports': exports,
    }


def extract(args):
    destination = pathlib.Path(args.directory).resolve()
    with tarfile.open(args.archive, 'r:gz') as source:
        # Only regular source files and directories within the expected single root.
        for member in source.getmembers():
            parts = pathlib.PurePosixPath(member.name).parts
            if not parts or parts[0] != 'libwebp-1.6.0' or '..' in parts or member.issym() or member.islnk():
                raise ValueError('Unexpected archive member: ' + member.name)
            target = destination.joinpath(*parts).resolve()
            if not target.is_relative_to(destination):
                raise ValueError('Archive path escaped source directory')
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as inp, target.open('wb') as out:
                    shutil.copyfileobj(inp, out)
            else:
                raise ValueError('Unexpected archive entry type: ' + member.name)


def verify(args):
    pins = json.loads(pathlib.Path(args.pins).read_text(encoding='utf-8-sig'))
    original = pathlib.Path(args.aar).read_bytes()
    if sha256(original) != pins['upstreamAar']['sha256']:
        raise ValueError('Upstream AAR SHA-256 mismatch')
    results, pending = [], []
    with zipfile.ZipFile(io.BytesIO(original)) as aar:
        for abi in pins['build']['abis']:
            with zipfile.ZipFile(io.BytesIO(aar.read(f'jni/{abi}/libffmpeg.zip.so'))) as payload:
                for name in pins['build']['libraryNames']:
                    old = elf_info(payload.read('usr/lib/' + name))
                    path = pathlib.Path(args.libraries) / abi / name
                    data = path.read_bytes()
                    # Release artifacts must not disclose a builder's local workspace.
                    for local_path in (str(pathlib.Path(args.source).resolve()), str(pathlib.Path(args.libraries).resolve())):
                        for spelling in (local_path, local_path.replace('\\', '/')):
                            if spelling.encode('utf-8').lower() in data.lower():
                                raise ValueError(f'{abi}/{name}: unstripped local build path')
                    new = elf_info(data)
                    expected_machine = {'arm64-v8a': 183, 'x86_64': 62}[abi]
                    if old['machine'] != new['machine'] or new['machine'] != expected_machine:
                        raise ValueError(f'{abi}/{name}: wrong ELF architecture')
                    if new['soname'] != old['soname'] or new['soname'] != name:
                        raise ValueError(f'{abi}/{name}: changed SONAME')
                    missing = old['exports'] - new['exports']
                    if missing:
                        raise ValueError(f'{abi}/{name}: missing exported ABI symbols: {sorted(missing)}')
                    allowed = set(pins['build']['libraryNames']) | {'libc.so', 'libm.so', 'libdl.so'}
                    if set(new['needed']) - allowed:
                        raise ValueError(f'{abi}/{name}: unexpected external dependencies')
                    if (not new['loadAlignments'] or min(new['loadAlignments']) < 16384
                            or not new['loadOffsetsCongruent'] or not new['relroEndsAligned']):
                        raise ValueError(f'{abi}/{name}: insufficient 16 KiB ELF/RELRO alignment')
                    entry = {k: v for k, v in new.items() if k != 'exports'}
                    entry.update(abi=abi, name=name, sha256=sha256(data),
                                 exportedSymbolCount=len(new['exports']),
                                 upstreamExportedSymbolCount=len(old['exports']),
                                 additionalExports=sorted(new['exports'] - old['exports']))
                    results.append(entry)
                    pending.append((path, pathlib.Path(args.output) / abi / name))
    # Publish only after all ten binaries passed the complete compatibility checks.
    for source, destination in pending:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    for name in ('COPYING', 'PATENTS', 'AUTHORS'):
        shutil.copyfile(pathlib.Path(args.source) / name, pathlib.Path(args.output) / name)
    manifest = {**pins, 'libraries': results}
    (pathlib.Path(args.output) / 'manifest.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'PASS: {len(results)} libraries; unchanged SONAME, no missing exports, 16 KiB LOAD and RELRO alignment')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    unpack = commands.add_parser('extract')
    unpack.add_argument('--archive', required=True)
    unpack.add_argument('--directory', required=True)
    check = commands.add_parser('verify')
    for field in ('pins', 'aar', 'libraries', 'output', 'source'):
        check.add_argument('--' + field, required=True)
    args = parser.parse_args()
    (extract if args.command == 'extract' else verify)(args)


if __name__ == '__main__':
    main()
