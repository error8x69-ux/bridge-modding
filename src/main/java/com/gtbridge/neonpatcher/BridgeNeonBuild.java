package com.gtbridge.neonpatcher;

import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class BridgeNeonBuild {
    static final String ORIGINAL = "de.smartrepair.gtvescbridge";
    static final String CLONE = "de.smartrepair.gtvescbridgx";
    static final String CLONE_LABEL = "GT Mod Bridge";
    private static final String ORIGINAL_LABEL = "GT VESC Bridge";
    private static final String ORIGINAL_FACTORY = "androidx.core.app.CoreComponentFactory";
    private static final String STATUS_FACTORY = "com.gtbridge.CloneStatusFactory";
    private static final String HELPER_OPTIONS = "assets/bridge-modding/options.bin";
    static final String PATCH_PROFILE = "assets/bridge-modding/applied-profile.json";

    static Map<Integer, Integer> palette(int background, int text, int accent) {
        return Map.ofEntries(
            Map.entry(0xff0b1218, background),
            Map.entry(0xff151f28, background),
            Map.entry(0xff1d2b36, mix(background, 0xffffffff, 16)),
            Map.entry(0xff293b48, mix(background, 0xffffffff, 48)),
            Map.entry(0xffedf2f6, text),
            Map.entry(0xff9cabb8, text),
            Map.entry(0xffee405e, accent),
            Map.entry(0xffd91e41, mix(accent, 0xff000000, 35)),
            Map.entry(0x22ee405e, 0x22000000 | (accent & 0xffffff))
        );
    }

    static int mix(int from, int to, int weight) {
        int result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int component = ((from >>> shift & 255) * (255 - weight)
                + (to >>> shift & 255) * weight + 127) / 255;
            result |= component << shift;
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        int patched = patch(Path.of(args[0]), Path.of(args[1]),
                            palette(0xff000000, 0xffff0046, 0xff00ffff));
        System.out.println("Patched DEX theme instructions: " + patched);
    }

    static int patch(Path source, Path output, Map<Integer, Integer> colors) throws Exception {
        return patch(source, output, colors, null, false, false, false, 0, 100, 0, false, false, null, false, false, null);
    }

    static int patch(Path source, Path output, Map<Integer, Integer> colors,
                     byte[] helperDex, boolean hideStatusBar, boolean compactNavigation, boolean hideReadingsPopup,
                     int onlineMask, int cockpitTextPercent, int hiddenReadings, boolean escFirst,
                     boolean holdSettings, CockpitGrid grid, boolean restrictedTripleTap, boolean keepPageOnResize,
                     byte[] appliedProfile)
            throws Exception {
        int dexColors = 0;
        int themeColors = 0;
        int manifestCount = 0;
        int labelCount = 0;
        int dexNumber = 0;
        var display = new BridgeNeonDex.DisplayPatch(hideStatusBar, compactNavigation, hideReadingsPopup || holdSettings,
            onlineMask, cockpitTextPercent, hiddenReadings, escFirst && grid == null, holdSettings, grid != null,
            restrictedTripleTap, keepPageOnResize);
        if (restrictedTripleTap && !display.hideReadingsPopup) {
            throw new IOException("Der Dreifachtipp setzt den Tippschutz voraus.");
        }
        if ((helperDex != null) != (hideStatusBar || holdSettings || grid != null || restrictedTripleTap)) {
            throw new IOException("Cockpit-App-Komponente fehlt oder wurde unerwartet übergeben.");
        }
        try (var input = new ZipFile(source.toFile());
             var counter = new CountingStream(Files.newOutputStream(output));
             var zip = new ZipOutputStream(counter)) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (appliedProfile != null && name.equals(PATCH_PROFILE)) {
                    throw new IOException("Die Quell-APK enthält bereits ein Mod-Profil.");
                }
                if (helperDex != null && name.equals(HELPER_OPTIONS)) {
                    throw new IOException("Die APK enthält bereits eine Cockpit-App-Komponente.");
                }
                if (name.matches("(?i)META-INF/([^/]+\\.(SF|RSA|DSA|EC)|MANIFEST\\.MF)")) {
                    continue;
                }
                byte[] data;
                if (entry.getSize() < 0 || entry.getSize() > 64L * 1024 * 1024) {
                    throw new IOException("APK-Eintrag zu groß");
                }
                try (var stream = input.getInputStream(entry)) {
                    data = stream.readNBytes((int) entry.getSize() + 1);
                    if (data.length != entry.getSize()) {
                        throw new IOException("APK-Eintrag hat eine ungültige Größe");
                    }
                }
                if (name.matches("classes[0-9]*\\.dex")) {
                    dexColors += patchDex(data, colors, display);
                    int number = name.equals("classes.dex") ? 1 : Integer.parseInt(name.substring(7, name.length() - 4));
                    dexNumber = Math.max(dexNumber, number);
                } else if (name.equals("AndroidManifest.xml") || name.equals("resources.arsc")) {
                    int packageCount = rename(data, ORIGINAL, CLONE);
                    if (packageCount == 0) {
                        throw new IllegalStateException("Missing package in " + name);
                    }
                    labelCount += renameResourceString(data, ORIGINAL_LABEL, CLONE_LABEL);
                    if (name.equals("AndroidManifest.xml")) {
                        manifestCount++;
                        if (keepPageOnResize) data = keepBridgeActivities(data);
                        if (helperDex != null && renameResourceString(data, ORIGINAL_FACTORY, STATUS_FACTORY) != 1) {
                            throw new IOException("Diese Bridge-Version unterstützt die gewählte Cockpit-App-Komponente nicht.");
                        }
                    }
                    if (!colors.isEmpty()) {
                        int patched = patchTypedColors(data, colors);
                        if (name.equals("resources.arsc")) themeColors += patched;
                    }
                } else if (!colors.isEmpty() && name.startsWith("res/") && name.endsWith(".xml")) {
                    patchTypedColors(data, colors);
                }
                writeEntry(zip, counter, entry, data);
            }
            if (appliedProfile != null) {
                var profile = new ZipEntry(PATCH_PROFILE);
                profile.setMethod(ZipEntry.DEFLATED);
                writeEntry(zip, counter, profile, appliedProfile);
            }
            if (helperDex != null) {
                var helper = new ZipEntry("classes" + (dexNumber + 1) + ".dex");
                helper.setMethod(ZipEntry.STORED);
                writeEntry(zip, counter, helper, helperDex);
                var options = new ZipEntry(HELPER_OPTIONS);
                options.setMethod(ZipEntry.STORED);
                int flags = (hideStatusBar ? 1 : 0) | (holdSettings ? 2 : 0) | (restrictedTripleTap ? 8 : 0);
                writeEntry(zip, counter, options, encodeHelperOptions(flags, grid, cockpitTextPercent));
            }
        }
        if (manifestCount != 1 || dexNumber == 0) {
            throw new IOException("Die Bridge-APK ist unvollständig");
        }
        if (!colors.isEmpty() && (dexColors < 30 || themeColors < 6)) {
            throw new IOException("Diese Bridge-Version nutzt ein anderes Farbschema");
        }
        if (labelCount == 0) {
            throw new IOException("Der App-Name wurde in der Original-APK nicht gefunden");
        }
        display.verify();
        return dexColors;
    }

    private static byte[] keepBridgeActivities(byte[] data) throws IOException {
        final int configResource = 0x0101001f;
        // orientation, screenLayout, screenSize, smallestScreenSize and density.
        final int sizeChanges = 0x1d80;
        int pool = readUnsignedShort(data, 2);
        int poolEnd = chunkEnd(data, pool, data.length);
        int map = poolEnd;
        int mapEnd = chunkEnd(data, map, data.length);
        if (readUnsignedShort(data, pool) != 1 || readUnsignedShort(data, map) != 0x0180) {
            throw new IOException("Android-Manifest für Bildschirmwechsel nicht unterstützt.");
        }
        int ids = map + readUnsignedShort(data, map + 2);
        int configName = -1;
        for (int entry = ids; entry + 4 <= mapEnd; entry += 4) {
            if (readInt(data, entry) == configResource) configName = (entry - ids) / 4;
        }
        if (configName < 0) throw new IOException("configChanges fehlt im Original-Manifest.");
        int patched = 0;
        for (int chunk = mapEnd; chunk < data.length;) {
            int end = chunkEnd(data, chunk, data.length);
            if (readUnsignedShort(data, chunk) == 0x0102 && end - chunk >= 36
                    && poolMatches(data, pool, poolEnd, readInt(data, chunk + 20), "activity")) {
                int start = chunk + 16 + readUnsignedShort(data, chunk + 24);
                int width = readUnsignedShort(data, chunk + 26);
                int count = readUnsignedShort(data, chunk + 28);
                if (width != 20 || start < chunk + 36 || start + count * width > end) {
                    throw new IOException("Ungültige Aktivitätsattribute im Android-Manifest.");
                }
                int config = -1, namespace = -1, insert = count;
                boolean target = false;
                for (int i = 0; i < count; i++) {
                    int attribute = start + i * width;
                    int name = readInt(data, attribute + 4);
                    int id = name >= 0 && name < (mapEnd - ids) / 4 ? readInt(data, ids + name * 4) : 0;
                    if (id > configResource && insert == count) insert = i;
                    if (id == configResource) config = attribute;
                    if (id == 0x01010003) {
                        namespace = readInt(data, attribute);
                        int value = readInt(data, attribute + 16);
                        target = poolMatches(data, pool, poolEnd, value, "de.smartrepair.smartbridge.MainActivity")
                            || poolMatches(data, pool, poolEnd, value, "de.smartrepair.smartbridge.GtVescDemoActivity");
                    }
                }
                if (target && config >= 0) {
                    if (Byte.toUnsignedInt(data[config + 15]) != 0x11) {
                        throw new IOException("Unerwarteter configChanges-Typ im Original-Manifest.");
                    }
                    writeInt(data, config + 16, readInt(data, config + 16) | sizeChanges);
                    patched++;
                } else if (target) {
                    int at = start + insert * width;
                    var expanded = Arrays.copyOf(data, data.length + width);
                    System.arraycopy(data, at, expanded, at + width, data.length - at);
                    writeInt(expanded, at, namespace);
                    writeInt(expanded, at + 4, configName);
                    writeInt(expanded, at + 8, -1);
                    writeInt(expanded, at + 12, 0x11000008);
                    writeInt(expanded, at + 16, sizeChanges);
                    writeInt(expanded, 4, expanded.length);
                    writeInt(expanded, chunk + 4, end - chunk + width);
                    writeShort(expanded, chunk + 28, count + 1);
                    for (int index = chunk + 30; index <= chunk + 34; index += 2) {
                        int before = readUnsignedShort(expanded, index);
                        if (before > insert) writeShort(expanded, index, before + 1);
                    }
                    data = expanded;
                    end += width;
                    patched++;
                }
            }
            chunk = end;
        }
        if (patched == 0) throw new IOException("Bridge-Aktivität im Original-Manifest nicht gefunden.");
        return data;
    }

    private static boolean poolMatches(byte[] data, int pool, int end, int index, String value) {
        int count = readInt(data, pool + 8);
        if (index < 0 || index >= count || index >= (end - pool - 28) / 4) return false;
        boolean utf8 = (readInt(data, pool + 16) & 0x100) != 0;
        int table = pool + readUnsignedShort(data, pool + 2);
        if (table + index * 4 > end - 4) return false;
        int start = pool + readInt(data, pool + 20) + readInt(data, table + index * 4);
        byte[] expected = value.getBytes(utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
        if (start < table || start > end - 2 - expected.length) return false;
        return readUnsignedShort(data, start) == (value.length() | (utf8 ? expected.length << 8 : 0))
            && Arrays.equals(data, start + 2, start + 2 + expected.length, expected, 0, expected.length);
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    record CockpitGrid(CockpitReading[] order, int background, int foreground, int accent) {
    }

    private static byte[] encodeHelperOptions(int flags, CockpitGrid grid, int textPercent) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var stream = new DataOutputStream(bytes)) {
            stream.writeByte(flags | (grid != null ? 4 : 0));
            if (grid != null) {
                stream.writeInt(grid.background());
                stream.writeInt(grid.foreground());
                stream.writeInt(grid.accent());
                stream.writeByte(textPercent);
                stream.writeByte(grid.order().length);
                for (var reading : grid.order()) stream.writeUTF(reading.name());
            }
        }
        return bytes.toByteArray();
    }

    private static int patchDex(byte[] data, Map<Integer, Integer> colors,
                                BridgeNeonDex.DisplayPatch display) throws Exception {
        rename(data, ORIGINAL, CLONE);
        int changed = new BridgeNeonDex(data).patchColors(colors, display);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(data, 32, data.length - 32);
        System.arraycopy(digest.digest(), 0, data, 12, 20);
        Adler32 checksum = new Adler32();
        checksum.update(data, 12, data.length - 12);
        writeInt(data, 8, (int) checksum.getValue());
        return changed;
    }

    private static int patchTypedColors(byte[] data, Map<Integer, Integer> colors) {
        int changed = 0;
        for (int offset = 0; offset <= data.length - 8; offset += 4) {
            if (data[offset] != 8 || data[offset + 1] != 0 || data[offset + 2] != 0) {
                continue;
            }
            int type = Byte.toUnsignedInt(data[offset + 3]);
            if (type >= 0x1c && type <= 0x1f) {
                Integer replacement = colors.get(readInt(data, offset + 4));
                if (replacement != null) {
                    writeInt(data, offset + 4, replacement);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static int renameResourceString(byte[] data, String from, String to) throws IOException {
        int end = chunkEnd(data, 0, data.length);
        for (int chunk = readUnsignedShort(data, 2); chunk < end; ) {
            int next = chunkEnd(data, chunk, end);
            if (readUnsignedShort(data, chunk) == 0x0001) {
                return replaceStringInPool(data, chunk, next, from, to);
            }
            chunk = next;
        }
        return 0;
    }

    private static int replaceStringInPool(byte[] data, int chunk, int end, String from, String to)
            throws IOException {
        int header = readUnsignedShort(data, chunk + 2);
        if (header < 28) {
            throw new IOException("Ungültiger Android-String-Pool");
        }
        int count = readInt(data, chunk + 8);
        int flags = readInt(data, chunk + 16);
        int strings = readInt(data, chunk + 20);
        int styles = readInt(data, chunk + 24);
        int stringsEnd = styles == 0 ? end - chunk : styles;
        if (count < 0 || strings < header || stringsEnd < strings || stringsEnd > end - chunk
                || count > (strings - header) / 4) {
            throw new IOException("Ungültige Android-String-Offsets");
        }

        boolean utf8 = (flags & 0x100) != 0;
        var charset = utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE;
        byte[] oldLabel = from.getBytes(charset);
        byte[] newLabel = to.getBytes(charset);
        if (oldLabel.length >= 128 || newLabel.length > oldLabel.length) {
            throw new IllegalArgumentException("Unsupported resource string replacement size");
        }
        int terminatorSize = utf8 ? 1 : 2;
        int oldByteLength = utf8 ? oldLabel.length : 0;
        int replaced = 0;
        for (int index = 0; index < count; index++) {
            int offset = readInt(data, chunk + header + index * 4);
            if (offset < 0 || offset > stringsEnd - strings - 2 - oldLabel.length - terminatorSize) {
                continue;
            }
            int start = chunk + strings + offset;
            int terminator = start + 2 + oldLabel.length;
            if (data[start] != from.length() || data[start + 1] != oldByteLength
                    || (utf8 ? data[terminator] : readUnsignedShort(data, terminator)) != 0
                    || !Arrays.equals(data, start + 2, terminator, oldLabel, 0, oldLabel.length)) {
                continue;
            }
            // Shorten the string in its existing slot so all resource indices stay valid.
            data[start] = (byte) to.length();
            data[start + 1] = (byte) (utf8 ? newLabel.length : 0);
            System.arraycopy(newLabel, 0, data, start + 2, newLabel.length);
            Arrays.fill(data, start + 2 + newLabel.length, terminator + terminatorSize, (byte) 0);
            replaced++;
        }
        if (replaced > 0) {
            writeInt(data, chunk + 16, flags & ~1);
        }
        return replaced;
    }

    private static int chunkEnd(byte[] data, int offset, int limit) throws IOException {
        if (offset < 0 || offset > limit - 8) {
            throw new IOException("Ungültige Android-Ressourcen");
        }
        int header = readUnsignedShort(data, offset + 2);
        int size = readInt(data, offset + 4);
        if (header < 8 || size < header || size > limit - offset) {
            throw new IOException("Ungültige Android-Chunk-Größe");
        }
        return offset + size;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | Byte.toUnsignedInt(data[offset + 1]) << 8;
    }

    private static int rename(byte[] data, String from, String to) {
        int matches = 0;
        for (var charset : new java.nio.charset.Charset[] {
            StandardCharsets.UTF_8, StandardCharsets.UTF_16LE
        }) {
            byte[] oldBytes = from.getBytes(charset);
            byte[] newBytes = to.getBytes(charset);
            if (oldBytes.length != newBytes.length) {
                throw new IllegalArgumentException("Replacement length differs");
            }
            for (int offset = 0; offset <= data.length - oldBytes.length; offset++) {
                if (Arrays.equals(data, offset, offset + oldBytes.length, oldBytes, 0, oldBytes.length)) {
                    System.arraycopy(newBytes, 0, data, offset, oldBytes.length);
                    matches++;
                    offset += oldBytes.length - 1;
                }
            }
        }
        return matches;
    }

    private static int readInt(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
            | Byte.toUnsignedInt(data[offset + 1]) << 8
            | Byte.toUnsignedInt(data[offset + 2]) << 16
            | data[offset + 3] << 24;
    }

    private static void writeInt(byte[] data, int offset, int value) {
        for (int index = 0; index < 4; index++) {
            data[offset + index] = (byte) (value >>> (8 * index));
        }
    }

    private static void writeEntry(ZipOutputStream zip, CountingStream counter,
                                   ZipEntry source, byte[] data)
            throws Exception {
        ZipEntry entry = new ZipEntry(source.getName());
        entry.setMethod(source.getMethod());
        // In-range DOS date avoids generated ZIP extra fields that would shift the padding.
        entry.setTime(946684800000L);
        if (entry.getMethod() == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            int alignment = source.getName().endsWith(".so") ? 16384 : 4;
            int headerLength = 30 + source.getName().getBytes(StandardCharsets.UTF_8).length;
            int remainder = (int) ((counter.size + headerLength) % alignment);
            if (remainder != 0 || alignment > 4) {
                int padding = (alignment - remainder) % alignment;
                while (padding < 6) padding += alignment;
                byte[] extra = new byte[padding];
                extra[0] = 0x35;
                extra[1] = (byte) 0xd9;
                extra[2] = (byte) (padding - 4);
                extra[3] = (byte) ((padding - 4) >>> 8);
                extra[4] = (byte) alignment;
                extra[5] = (byte) (alignment >>> 8);
                entry.setExtra(extra);
            }
        }
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static final class CountingStream extends FilterOutputStream {
        long size;

        CountingStream(OutputStream output) { super(output); }

        @Override public void write(int value) throws IOException {
            out.write(value);
            size++;
        }

        @Override public void write(byte[] data, int offset, int length) throws IOException {
            out.write(data, offset, length);
            size += length;
        }
    }
}
