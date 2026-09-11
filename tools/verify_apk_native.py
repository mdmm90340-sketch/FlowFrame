"""Recursively inspect APK/AAR ELF LOAD and GNU_RELRO alignment, without execution.

Usage: python tools/verify_apk_native.py app.apk [lib/]
For an AAR, use jni/arm64-v8a/ or jni/x86_64/ as the prefix.
Exit 0 means both static checks passed for every ELF, not runtime compatibility.
Missing RELRO is reported as unverified, separately from a misaligned RELRO.
"""
import argparse
import io
import json
import struct
import zipfile

PAGE_SIZE = 16384
ZIP_SIGNATURES = (b'PK\x03\x04', b'PK\x05\x06', b'PK\x07\x08')


def inspect_elf(data, name):
    record = {'name': name, 'load_16k_aligned': None,
              'relro_16k_aligned': None, 'relro_status': 'unverified',
              'static_alignment_checks_passed': False}
    try:
        if len(data) < 64 or data[:4] != b'\x7fELF':
            raise ValueError('Truncated or invalid ELF header')
        if data[4] != 2:
            raise ValueError('Only ELF64 is supported by this checker')
        if data[5] not in (1, 2) or data[6] != 1:
            raise ValueError('Invalid ELF byte order or identification version')
        endian = '<' if data[5] == 1 else '>'
        record['machine'] = struct.unpack_from(endian + 'H', data, 18)[0]
        if struct.unpack_from(endian + 'I', data, 20)[0] != 1:
            raise ValueError('Invalid ELF version')
        phoff = struct.unpack_from(endian + 'Q', data, 32)[0]
        ehsize, entsize, count = struct.unpack_from(endian + 'HHH', data, 52)
        if ehsize != 64 or (count and (entsize < 56 or phoff < ehsize)):
            raise ValueError('Invalid ELF/program header size or offset')
        if count == 0xffff:
            raise ValueError('Extended program-header counts are not supported')
        if phoff + count * entsize > len(data):
            raise ValueError('Truncated ELF program-header table')
        loads, relro = [], []
        for index in range(count):
            header = struct.unpack_from(endian + 'IIQQQQQQ', data, phoff + index * entsize)
            kind, _, offset, vaddr, _, filesz, memsz, align = header
            if offset + filesz > len(data):
                raise ValueError('ELF segment extends beyond file')
            if kind == 1:
                if memsz < filesz or (align > 1 and align & (align - 1)):
                    raise ValueError('Invalid LOAD memory size or alignment')
                loads.append({'offset': offset, 'vaddr': vaddr, 'align': align})
            elif kind == 0x6474e552:
                if memsz < filesz:
                    raise ValueError('Invalid GNU_RELRO memory size')
                relro.append({'vaddr': vaddr, 'memsz': memsz, 'end': vaddr + memsz,
                              'end_16k_aligned': (vaddr + memsz) % PAGE_SIZE == 0})
        load_ok = bool(loads) and all(
            item['align'] >= PAGE_SIZE
            and item['offset'] % PAGE_SIZE == item['vaddr'] % PAGE_SIZE for item in loads)
        relro_ok = all(item['end_16k_aligned'] for item in relro) if relro else None
        record.update(load_segments=loads, load_16k_aligned=load_ok,
                      relro_segments=relro, relro_16k_aligned=relro_ok,
                      relro_status=('aligned' if relro_ok else 'misaligned') if relro else 'absent',
                      static_alignment_checks_passed=load_ok and relro_ok is True)
    except (ValueError, struct.error) as error:
        record['error'] = str(error)
    return record


def inspect_archive(path, prefix='lib/'):
    records, containers, errors = [], [], []

    def inspect(data, name):
        if data.startswith(b'\x7fELF'):
            records.append(inspect_elf(data, name))
        elif data.startswith(ZIP_SIGNATURES):
            containers.append(name)
            try:
                with zipfile.ZipFile(io.BytesIO(data)) as nested:
                    for item in nested.infolist():
                        if not item.is_dir():
                            inspect(nested.read(item), name + '!/' + item.filename)
            except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, NotImplementedError) as error:
                errors.append({'name': name, 'error': str(error)})

    abis = []
    try:
        with zipfile.ZipFile(path) as archive:
            native = [item for item in archive.infolist()
                      if item.filename.startswith(prefix) and not item.is_dir()]
            abis = sorted({item.filename.split('/')[1] for item in native
                           if len(item.filename.split('/')) > 1})
            for item in native:
                inspect(archive.read(item), item.filename)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, NotImplementedError) as error:
        errors.append({'name': str(path), 'error': str(error)})
    counts = {
        'load_misaligned_count': sum(item['load_16k_aligned'] is False for item in records),
        'relro_misaligned_count': sum(item['relro_16k_aligned'] is False for item in records),
        'relro_absent_count': sum(item['relro_status'] == 'absent' for item in records),
        'elf_format_error_count': sum('error' in item for item in records),
        'container_error_count': len(errors),
    }
    return {
        'abis': abis, 'elf_count': len(records), **counts,
        'all_load_16k_aligned': bool(records) and all(item['load_16k_aligned'] is True for item in records),
        'all_relro_16k_aligned': bool(records) and all(item['relro_16k_aligned'] is True for item in records),
        'all_static_alignment_checks_passed': bool(records) and not errors
            and all(item['static_alignment_checks_passed'] for item in records),
        'scope': 'Static ELF LOAD and GNU_RELRO checks only; does not prove runtime compatibility.',
        'missing_relro_policy': 'Absent RELRO is unverified, not a measured misalignment; overall checks remain incomplete.',
        'containers': containers, 'errors': errors, 'elf': records,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive')
    parser.add_argument('prefix', nargs='?', default='lib/')
    args = parser.parse_args()
    result = inspect_archive(args.archive, args.prefix)
    print(json.dumps(result, indent=2))
    return 0 if result['all_static_alignment_checks_passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
