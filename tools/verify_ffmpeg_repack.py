"""Verify that repacking preserves every entry and Unix mode except ten library payloads.

Usage: python tools/verify_ffmpeg_repack.py original.aar patched.aar native/webp-1.6.0/manifest.json
"""
import io, json, stat, sys, zipfile

original, patched = map(zipfile.ZipFile, sys.argv[1:3])
manifest = json.load(open(sys.argv[3], encoding='utf-8'))
expected = {'jni/' + abi + '/libffmpeg.zip.so' for abi in ('arm64-v8a', 'x86_64')}
assert original.namelist() == patched.namelist(), 'AAR entries changed'
report = []
for name in original.namelist():
    before, after = original.read(name), patched.read(name)
    assert original.getinfo(name).external_attr == patched.getinfo(name).external_attr, name
    if name not in expected:
        assert before == after, 'Unexpected AAR change: ' + name
        continue
    old = zipfile.ZipFile(io.BytesIO(before))
    new = zipfile.ZipFile(io.BytesIO(after))
    assert old.namelist() == new.namelist(), 'Native entries changed'
    abi = name.split('/')[1]
    allowed = {'usr/lib/' + x['name'] for x in manifest['libraries'] if x['abi'] == abi}
    changes, symlinks = [], 0
    for member in old.namelist():
        a, b = old.getinfo(member), new.getinfo(member)
        assert a.external_attr == b.external_attr and a.create_system == b.create_system, 'Unix metadata changed: ' + member
        symlinks += stat.S_ISLNK(a.external_attr >> 16)
        if old.read(member) != new.read(member):
            assert member in allowed, 'Unexpected native change: ' + member
            changes.append(member)
    assert set(changes) == allowed
    report.append({'abi': abi, 'symlinks_preserved': symlinks, 'only_changed_libraries': changes})
print(json.dumps(report, indent=2))
