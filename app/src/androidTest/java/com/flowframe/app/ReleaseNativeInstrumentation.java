package com.flowframe.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Framework-only verification of the actual minified app and its installed native runtime. */
public final class ReleaseNativeInstrumentation extends Instrumentation {
    private static final String MANIFEST = "open_source/WEBP-NATIVE-MANIFEST.json";
    private static final String DIRECTORY_PREFIX = "flowframe-release-native-";
    private static final long INITIALIZATION_TIMEOUT_MS = 90_000L;
    private static final long PROCESS_TIMEOUT_MS = 60_000L;
    private static final Set<String> LIBRARY_NAMES = new HashSet<>(Arrays.asList(
            "libsharpyuv.so", "libwebpdecoder.so", "libwebp.so", "libwebpdemux.so", "libwebpmux.so"));

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    /** Instrumentation.start() invokes this on its worker thread, not the app's main thread. */
    @Override
    public void onStart() {
        final Bundle result = new Bundle();
        File testDirectory = null;
        Activity launchedActivity = null;
        boolean passed = false;
        try {
            Context target = getTargetContext();
            Intent launcher = target.getPackageManager().getLaunchIntentForPackage(target.getPackageName());
            require(launcher != null, "The target app has no launcher Activity");
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchedActivity = startActivitySync(launcher);
            require(launchedActivity != null, "The real launcher Activity did not start");
            waitForIdleSync();
            result.putBoolean("launcher_started", true);
            result.putInt("api", Build.VERSION.SDK_INT);
            progress("Real release launcher started; checking installed native libraries");

            JSONObject manifest;
            try (InputStream input = target.getAssets().open(MANIFEST)) {
                manifest = new JSONObject(readUtf8(input, 256 * 1024));
            }
            String abi = chooseAbi(manifest.getJSONArray("libraries"));
            Map<String, String> expected = expectedLibraries(manifest.getJSONArray("libraries"), abi);
            File packages = new File(target.getNoBackupFilesDir(), "youtubedl-android/packages");
            File installedLibraries = new File(packages, "ffmpeg/usr/lib");
            awaitLibraryHashes(installedLibraries, expected);
            result.putString("abi", abi);
            result.putInt("libraries_verified", expected.size());
            progress("All five installed " + abi + " native libraries match the bundled manifest");

            File cache = target.getCacheDir().getCanonicalFile();
            testDirectory = new File(cache, DIRECTORY_PREFIX + UUID.randomUUID());
            require(testDirectory.mkdirs(), "Could not create the generated-fixture directory");
            File png = new File(testDirectory, "generated.png");
            File webp = new File(testDirectory, "encoded.webp");
            File mp4 = new File(testDirectory, "generated.mp4");
            generatePng(png);

            String nativeDirectory = target.getApplicationInfo().nativeLibraryDir;
            File executable = new File(nativeDirectory, "libffmpeg.so");
            require(executable.isFile() && executable.canExecute(), "Bundled FFmpeg is unavailable");
            String libraryPath = nativeDirectory + File.pathSeparator
                    + new File(packages, "python/usr/lib").getAbsolutePath() + File.pathSeparator
                    + installedLibraries.getAbsolutePath();

            runFfmpeg(executable, libraryPath, "WebP encoding",
                    "-i", png.getAbsolutePath(), "-frames:v", "1", "-c:v", "libwebp",
                    "-q:v", "85", webp.getAbsolutePath());
            require(webp.isFile() && webp.length() > 12, "WebP encoding produced no image");
            verifyWebpHeader(webp);
            result.putString("webp_encoder", "libwebp");
            progress("Bundled libwebp encoded the generated PNG successfully");

            runFfmpeg(executable, libraryPath, "WebP decoding and H.264 encoding",
                    "-f", "image2", "-loop", "1", "-framerate", "24", "-i", webp.getAbsolutePath(),
                    "-t", "1", "-vf", "scale=320:240:flags=bicubic,format=yuv420p",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23", "-an",
                    "-movflags", "+faststart", mp4.getAbsolutePath());
            require(mp4.isFile() && mp4.length() > 512, "H.264 encoding produced no video");
            runFfmpeg(executable, libraryPath, "Full MP4 decoding",
                    "-xerror", "-i", mp4.getAbsolutePath(), "-map", "0:v:0", "-f", "null", "-");
            long duration = verifyVideo(mp4);
            result.putString("video_dimensions", "320x240");
            result.putLong("video_duration_ms", duration);
            result.putBoolean("full_ffmpeg_decode", true);
            result.putBoolean("android_frame_decode", true);
            result.putInt("ffmpeg_commands", 3);
            result.putString("stream", "\nPASS: real release startup; five native SHA-256 checks; "
                    + "PNG -> libwebp -> 320x240 H.264 MP4; full FFmpeg and Android decoding; "
                    + "duration=" + duration + " ms; ABI=" + abi + "\n");
            passed = true;
        } catch (Throwable error) {
            result.putString("failure", error.getClass().getName() + ": " + error.getMessage());
            result.putString("stream", "\nFAIL: " + result.getString("failure") + "\n");
        } finally {
            if (testDirectory != null) {
                try {
                    deleteTestDirectory(testDirectory, getTargetContext().getCacheDir());
                } catch (Throwable cleanupError) {
                    passed = false;
                    result.putString("cleanup_failure", cleanupError.toString());
                    result.putString("stream", result.getString("stream", "")
                            + "Fixture cleanup failed: " + cleanupError.getMessage() + "\n");
                }
            }
            if (launchedActivity != null) {
                final Activity activity = launchedActivity;
                runOnMainSync(new Runnable() {
                    @Override public void run() { activity.finish(); }
                });
            }
            result.putBoolean("passed", passed);
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private void progress(String message) {
        Bundle status = new Bundle();
        status.putString("stream", message + "\n");
        sendStatus(0, status);
    }

    private static String chooseAbi(JSONArray libraries) throws Exception {
        for (String abi : Build.SUPPORTED_ABIS) {
            for (int index = 0; index < libraries.length(); index++) {
                if (abi.equals(libraries.getJSONObject(index).getString("abi"))) return abi;
            }
        }
        throw new IllegalStateException("No device ABI is present in the native manifest");
    }

    private static Map<String, String> expectedLibraries(JSONArray libraries, String abi) throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        for (int index = 0; index < libraries.length(); index++) {
            JSONObject item = libraries.getJSONObject(index);
            if (!abi.equals(item.getString("abi"))) continue;
            String name = item.getString("name");
            String hash = item.getString("sha256");
            require(LIBRARY_NAMES.contains(name) && hash.matches("[0-9a-f]{64}"), "Invalid native manifest entry");
            require(expected.put(name, hash) == null, "Duplicate native manifest entry");
        }
        require(expected.keySet().equals(LIBRARY_NAMES), "Native manifest must list exactly five expected libraries");
        return expected;
    }

    private static void awaitLibraryHashes(File directory, Map<String, String> expected) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + INITIALIZATION_TIMEOUT_MS;
        String pending = "Native initialization has not completed";
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean complete = true;
            for (Map.Entry<String, String> item : expected.entrySet()) {
                File library = new File(directory, item.getKey());
                try {
                    if (!library.isFile() || !item.getValue().equals(sha256(library))) {
                        complete = false;
                        pending = item.getKey() + " is missing or has not reached the expected SHA-256";
                        break;
                    }
                } catch (IOException writing) {
                    complete = false;
                    pending = item.getKey() + " is not ready to read";
                    break;
                }
            }
            if (complete) return;
            Thread.sleep(200L);
        }
        throw new IllegalStateException("Native initialization timed out: " + pending);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private static void generatePng(File output) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(83, 62, 191));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(30, 203, 185));
            canvas.drawCircle(160, 105, 62, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(65, 191, 255, 203, paint);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "Could not encode the generated PNG");
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static void verifyWebpHeader(File file) throws IOException {
        byte[] header = new byte[12];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) break;
                offset += count;
            }
            require(offset == 12 && "RIFF".equals(new String(header, 0, 4, "US-ASCII"))
                    && "WEBP".equals(new String(header, 8, 4, "US-ASCII")), "Encoder output is not WebP");
        }
    }

    private static long verifyVideo(File output) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(output.getAbsolutePath());
            require("320".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)), "Video width is incorrect");
            require("240".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)), "Video height is incorrect");
            long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            require(Math.abs(duration - 1_000L) <= 200L, "Video duration is incorrect: " + duration);
            Bitmap frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST);
            require(frame != null, "Android could not decode a video frame");
            frame.recycle();
            return duration;
        } finally {
            retriever.release();
        }
    }

    private static void runFfmpeg(File executable, String libraryPath, String label, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList(executable.getAbsolutePath(), "-hide_banner", "-v", "error", "-nostdin", "-y"));
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH", libraryPath);
        final Process process = builder.start();
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                try (InputStream input = process.getInputStream()) {
                    byte[] buffer = new byte[4_096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        synchronized (captured) {
                            int remaining = 65_536 - captured.size();
                            if (remaining > 0) captured.write(buffer, 0, Math.min(count, remaining));
                        }
                    }
                } catch (IOException ignored) {
                    // The main thread closes this stream when a process is canceled or times out.
                }
            }
        }, "FlowFrame-release-native-output");
        reader.setDaemon(true);
        reader.start();
        try {
            long deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MS;
            int exitCode;
            while (true) {
                try {
                    exitCode = process.exitValue();
                    break;
                } catch (IllegalThreadStateException running) {
                    require(SystemClock.elapsedRealtime() < deadline, label + " timed out");
                    Thread.sleep(100L);
                }
            }
            reader.join(1_000L);
            synchronized (captured) {
                require(exitCode == 0, label + " failed (" + exitCode + "): " + captured.toString("UTF-8"));
            }
        } finally {
            process.destroy();
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            reader.join(1_000L);
        }
    }

    private static String readUtf8(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4_096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            require(bytes.size() + count <= maximumBytes, "Native manifest exceeds the expected size");
            bytes.write(buffer, 0, count);
        }
        return bytes.toString("UTF-8");
    }

    private static void deleteTestDirectory(File directory, File cache) throws IOException {
        File canonical = directory.getCanonicalFile();
        require(canonical.getParentFile().equals(cache.getCanonicalFile())
                && canonical.getName().startsWith(DIRECTORY_PREFIX), "Refusing cleanup outside this test's cache directory");
        deleteGeneratedTree(canonical, canonical);
    }

    private static void deleteGeneratedTree(File file, File root) throws IOException {
        String canonical = file.getCanonicalPath();
        require(canonical.equals(root.getPath()) || canonical.startsWith(root.getPath() + File.separator), "Fixture cleanup crossed its directory boundary");
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteGeneratedTree(child, root);
        require(!file.exists() || file.delete(), "Could not delete generated fixture " + file.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
