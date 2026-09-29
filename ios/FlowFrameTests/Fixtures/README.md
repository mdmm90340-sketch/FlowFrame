# Media exporter fixtures

These two small files are synthetic test inputs, generated locally with FFmpeg
7.1. They contain no downloaded media, recordings, personal data, or network
references, and may be used under this repository's license.

- `fixture-video-h264.mp4`: one second of a solid purple frame, 64 × 64,
  30 frames per second, H.264 Constrained Baseline, `yuv420p`, no audio; 1,886 bytes.
- `fixture-audio-aac.m4a`: one second of a generated 440 Hz sine wave, AAC-LC,
  44,100 Hz, mono; 9,361 bytes.

The tests load these resources from the XCTest bundle and write each result to
an independent temporary directory. They exercise the real `MediaExporter`.
Missing resources fail the test. The files are read-only inputs and are never
replaced by a mock or skipped on CI.

CI run `36595952336` failed twice while generating H.264 test inputs, before
calling the exporter, then succeeded in the same fixture helper in a later
test. Fixed inputs remove that simulator encoder readiness dependency from
tests whose purpose is media validation, merging, and cancellation. They do not
establish which simulator or encoder scheduling behavior caused the delay.

## Regeneration

Run with FFmpeg 7.1 in this directory. A different FFmpeg/libx264 version may
produce different bytes; review the media and update the hashes if regenerating.

```sh
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i color=c=0x7255ed:s=64x64:r=30:d=1 -an -c:v libx264 -profile:v baseline -level:v 3.0 -pix_fmt yuv420p -preset medium -crf 28 -threads 1 -map_metadata -1 -fflags +bitexact -flags:v +bitexact -movflags +faststart fixture-video-h264.mp4
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i sine=frequency=440:sample_rate=44100:duration=1 -vn -c:a aac -profile:a aac_low -b:a 64k -ac 1 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -movflags +faststart fixture-audio-aac.m4a
```

Both files were fully decoded successfully using `ffmpeg -v error -xerror -i
FILE -f null -`. `ffprobe` confirmed one second of video only and one second of
audio only, respectively.

## SHA-256

```text
65f3dbf7b5b7bbfe877632cad85c82e8cac607115b4044d39cab044f74b8348c  fixture-video-h264.mp4
e8f6e075cebf7fe945ea08f2d6bd74763ed3996836842051fdf0fbb46e899eb1  fixture-audio-aac.m4a
```
