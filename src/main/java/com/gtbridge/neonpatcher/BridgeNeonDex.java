package com.gtbridge.neonpatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.gtbridge.neonpatcher.CockpitReading.*;

final class BridgeNeonDex {
    private static final String MAIN_ACTIVITY = "Lde/smartrepair/smartbridge/MainActivity;";
    private static final String WORKSPACE = "Lde/smartrepair/smartbridge/GtVescWorkspaceUi;";
    private static final String INSTRUMENT = "Lde/smartrepair/smartbridge/GtVescInstrumentPanel;";
    private static final String COCKPIT_VIEW = "Lde/smartrepair/smartbridge/GtVescCockpitView;";
    private final byte[] dex;
    private final int strings;
    private final int types;
    private final int methods;

    BridgeNeonDex(byte[] dex) {
        this.dex = dex;
        if (dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x'
                || u32(36) != 112 || u32(40) != 0x12345678) {
            throw new IllegalArgumentException("Unbekanntes DEX-Format");
        }
        strings = u32(60);
        types = u32(68);
        methods = u32(92);
    }

    int patchColors(Map<Integer, Integer> colors, DisplayPatch display) throws IOException {
        boolean recolor = !colors.isEmpty();
        if (!recolor && !display.hideStatusBar && !display.compactNavigation
                && !display.hideReadingsPopup && display.onlineMask == 0
                && display.cockpitTextPercent == 100 && display.hiddenReadings == 0 && !display.escFirst) {
            return 0;
        }
        int count = 0;
        var dashboard = recolor ? dashboardColors(colors) : colors;
        var instruments = recolor ? instrumentColors(colors) : colors;
        var instrumentAccents = new HashMap<>(instruments);
        var cockpitIcons = new HashMap<>(colors);
        if (recolor) {
            int accent = colors.get(0xffee405e);
            replaceColors(instrumentAccents, accent, 0xffff3659, 0xffff6074);
            instrumentAccents.put(0x00ff3659, withAlpha(accent, 0));
            cockpitIcons.put(0xffee405e, colors.get(0xffedf2f6));
        }
        int classCount = u32(96);
        int classOffset = u32(100);
        for (int index = 0; index < classCount; index++) {
            int entry = classOffset + 32 * index;
            String name = string(u32(types + 4 * u32(entry)));
            int data = u32(entry + 24);
            if (data == 0 || !name.startsWith("Lde/smartrepair/smartbridge/")) {
                continue;
            }
            Cursor cursor = new Cursor(data);
            int fieldCount = cursor.uleb() + cursor.uleb();
            int directCount = cursor.uleb();
            int virtualCount = cursor.uleb();
            for (int field = 0; field < fieldCount; field++) {
                cursor.uleb();
                cursor.uleb();
            }
            Map<Integer, Integer> classColors = colors;
            Map<String, Map<Integer, Integer>> methodColors = Map.of();
            if (name.endsWith("/GtVescInstrumentPanel;")) {
                classColors = instruments;
                methodColors = Map.of("dial", instrumentAccents, "bar", instrumentAccents,
                                      "taperedaccent", instrumentAccents);
            } else if (name.endsWith("/MainActivity$LuxuryCockpitView;")) {
                classColors = dashboard;
            } else if (name.endsWith("/GtVescCockpitView;")) {
                methodColors = Map.of("icon", cockpitIcons);
            }
            count += patchMethods(cursor, directCount, classColors, methodColors, name, display);
            count += patchMethods(cursor, virtualCount, classColors, methodColors, name, display);
        }
        return count;
    }

    private static Map<Integer, Integer> dashboardColors(Map<Integer, Integer> colors) {
        var result = new HashMap<>(colors);
        int background = colors.get(0xff0b1218);
        int text = colors.get(0xffedf2f6);
        int accent = colors.get(0xffee405e);
        int subduedAccent = colors.get(0xffd91e41);
        int panelEdge = colors.get(0xff1d2b36);
        replaceColors(result, background,
            0xff010101, 0xff020202, 0xff020303, 0xff030303, 0xff030404, 0xff050505,
            0xff070707, 0xff080706, 0xff080909, 0xff090909, 0xff0a0908, 0xff0a0a09,
            0xff0b0a09, 0xff0b0b0a, 0xff0e0c09, 0xff10100f, 0xff11100e, 0xff111212,
            0xff17110a, 0xff181818);
        replaceColors(result, text,
            0xff8f8f90, 0xffaaa9a8, 0xffaaaaaa, 0xffb4b4b3, 0xffc9c9c9,
            0xffd8d8d8, 0xffdadadd, 0xffe1e1e1, 0xffe7e8ea, 0xffff6c1b);
        replaceColors(result, accent, 0xffefefef, 0xfff2f2f2, 0xffd66a0a);
        replaceColors(result, subduedAccent, 0xffb1b1b1, 0xffb76500);
        replaceColors(result, panelEdge, 0xff282018, 0xff28231f);
        replaceColors(result, withAlpha(accent, 0x88), 0x889e4d00, 0x884a2300, 0x88ff8300);
        replaceColors(result, withAlpha(accent, 0x66), 0x668b3f00);
        replaceColors(result, withAlpha(accent, 0x55),
            0xff6f3a0b, 0xff5f360b, 0xff6b3707, 0xff754109,
            0xff653809, 0xff6b3b0a, 0xff6e3908, 0xff754209);
        return result;
    }

    private static Map<Integer, Integer> instrumentColors(Map<Integer, Integer> colors) {
        var result = new HashMap<>(colors);
        int background = colors.get(0xff0b1218);
        int text = colors.get(0xffedf2f6);
        int accent = colors.get(0xffee405e);
        replaceColors(result, background, 0xff0b1920, 0xff050e13, 0xff070e14);
        replaceColors(result, text, 0xffedf2f5, 0xffe8e6e9, 0xff9aa8b5,
            0xff72818d, 0xffb8c4ce);
        replaceColors(result, accent, 0xffe81b41, 0xffe91940, 0xffdce8ee);
        replaceColors(result, BridgeNeonBuild.mix(background, accent, 150),
            0xffbe1333, 0xffa1b3bf);
        replaceColors(result, BridgeNeonBuild.mix(background, accent, 95),
            0xff344853, 0xff667a88, 0xff41535f);
        replaceColors(result, BridgeNeonBuild.mix(background, accent, 48),
            0xff253640, 0xff162932, 0xff3d525f, 0xff30424e);
        replaceColors(result, BridgeNeonBuild.mix(background, accent, 28),
            0xff111e27, 0xff5b0c1c);
        result.put(0xffed244a, BridgeNeonBuild.mix(background, accent, 62));
        return result;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xffffff);
    }

    private static void replaceColors(Map<Integer, Integer> colors, int value, int... originals) {
        for (int original : originals) colors.put(original, value);
    }

    private int patchMethods(Cursor cursor, int count, Map<Integer, Integer> colors,
                             Map<String, Map<Integer, Integer>> methodColors,
                             String owner, DisplayPatch display) throws IOException {
        int index = 0;
        int changed = 0;
        for (int method = 0; method < count; method++) {
            index += cursor.uleb();
            cursor.uleb();
            int code = cursor.uleb();
            String name = string(u32(methods + 8 * index + 4)).toLowerCase(Locale.ROOT);
            if (code != 0 && !name.matches(".*(fault|error|warn|danger|overheat|disconnect).*")) {
                var replacements = methodColors.getOrDefault(name, colors);
                if (!replacements.isEmpty()) changed += patchCode(code, replacements);
                patchDisplay(code, owner, name, display);
                if (INSTRUMENT.equals(owner)) {
                    patchInstrument(code, name, display);
                    if (display.customGrid) verifyGridMethod(index, name, display);
                }
                if (COCKPIT_VIEW.equals(owner)) {
                    if (display.holdSettings) verifyHoldSettings(code, name, display);
                    if ("<init>".equals(name)) {
                        if (display.hideReadingsPopup) patchInstrumentTap(code, display);
                        if (display.tripTargetHidden) patchTripTarget(code, display);
                        if (display.customGrid && hasConstString(code, 0x11, "fixedCockpit030")
                                && hasConstString(code, 0x22, "cockpitInstrument030")
                                && invokesAt(code, 0x2d, 0x306e, 0x0765, "Landroid/widget/FrameLayout;", "addView")) {
                            display.applied |= DisplayPatch.GRID_TARGET;
                        }
                    }
                }
                if (display.restrictedTripleTap && MAIN_ACTIVITY.equals(owner)) {
                    verifyRestrictedAction(code, name, display);
                }
                patchOnline(code, owner, name, display);
            }
        }
        return changed;
    }

    private void patchInstrument(int code, String name, DisplayPatch options) throws IOException {
        if (options.cockpitTextPercent != 100 && name.equals("text")) {
            int start = instructionOffset(code, 0, 23);
            int field = u16(start + 8);
            int size = u16(start + 22);
            int face = u16(start + 32);
            int align = u16(start + 42);
            if (u16(start + 6) != 0x1054 || u16(start + 16) != 0x1754
                    || u16(start + 18) != field || u16(start + 20) != 0x206e
                    || u16(start + 24) != 0x0067 || !methodIs(size, "Landroid/graphics/Paint;", "setTextSize")
                    || u16(start + 26) != 0x1654 || u16(start + 28) != field
                    || u16(start + 30) != 0x206e || u16(start + 34) != 0x0086
                    || !methodIs(face, "Landroid/graphics/Paint;", "setTypeface")
                    || u16(start + 36) != 0x1654 || u16(start + 38) != field
                    || u16(start + 40) != 0x206e || u16(start + 44) != 0x0096
                    || !methodIs(align, "Landroid/graphics/Paint;", "setTextAlign")) {
                throw unsupportedCockpit("Cockpit-Schrift");
            }
            // Reuse v0 (Paint) and v7 (color, already consumed) to scale v6 (text size).
            int factor = Float.floatToRawIntBits(options.cockpitTextPercent / 100f) >>> 16;
            int[] words = {0x0715, factor, 0x76c8, 0x206e, size, 0x0060,
                0x206e, face, 0x0080, 0x206e, align, 0x0090, 0, 0, 0};
            for (int i = 0; i < words.length; i++) writeShort(start + 2 * (8 + i), words[i]);
            options.applied |= DisplayPatch.COCKPIT_TEXT;
        }
        if (options.hides(DRIVE) && name.equals("ondraw")) {
            for (int i = 0, units = u32(code + 12); i < units;) {
                int offset = code + 16 + 2 * i;
                int word = u16(offset);
                int length = width(word, offset);
                if (length <= 0 || length > units - i) throw unsupportedCockpit("Antriebsblock");
                if (word == 0x2070 && methodIs(u16(offset + 2), INSTRUMENT, "motors")) {
                    eraseInvoke(offset);
                    options.appliedReadings |= DRIVE.bit;
                }
                i += length;
            }
        }
        switch (name) {
            case "battery":
                if (options.hides(VOLTAGE)) {
                    eraseDraw(code, VOLTAGE, "line", 0x62);
                    eraseDraw(code, VOLTAGE, "text", 0x7d);
                    options.appliedReadings |= VOLTAGE.bit;
                }
                break;
            case "power":
                if (options.hides(POWER)) {
                    eraseDraw(code, POWER, "fadedLine", 0x0b, 0x15);
                    eraseDraw(code, POWER, "text", 0x27, 0x6a);
                    options.appliedReadings |= POWER.bit;
                }
                if (options.hides(CURRENT)) {
                    eraseDraw(code, CURRENT, "fadedLine", 0x76, 0x7f);
                    eraseDraw(code, CURRENT, "text", 0x92, 0xac);
                    options.appliedReadings |= CURRENT.bit;
                }
                break;
            case "motor":
                patchMotorReadings(code, options);
                break;
            case "footer":
                if (options.hides(TRIP)) {
                    eraseDraw(code, TRIP, "label", 0x15);
                    eraseDraw(code, TRIP, "text", 0x2c, 0x5b);
                    options.appliedReadings |= TRIP.bit;
                }
                if (options.hides(CONSUMPTION)) {
                    eraseDraw(code, CONSUMPTION, "label", 0x75);
                    eraseDraw(code, CONSUMPTION, "text", 0x8d, 0x9b, 0xbc);
                    options.appliedReadings |= CONSUMPTION.bit;
                }
                if (options.hides(TRIP) || options.hides(CONSUMPTION)) {
                    eraseDraw(code, TRIP, "line", 0x6e);
                }
                if (options.hides(TRIP) && options.hides(CONSUMPTION)) {
                    eraseDraw(code, TRIP, "fadedLine", 0x0b, 0xc8);
                }
                break;
            default:
                break;
        }
    }

    private void patchMotorReadings(int code, DisplayPatch options) throws IOException {
        if (options.hides(PHASE)) {
            eraseDraw(code, PHASE, "label", 0x0c);
            eraseDraw(code, PHASE, "text", 0x30);
            eraseDraw(code, PHASE, "bar", 0x42);
            options.appliedReadings |= PHASE.bit;
        }
        if (options.escFirst) {
            swapReadingStrings(code, 0x57, "Motor", 0x69, "ESC");
            swapReadingStrings(code, 0x70, "temperature", 0x96, "controller");
            options.applied |= DisplayPatch.ESC_FIRST;
        }
        var left = options.escFirst ? ESC_TEMP : MOTOR_TEMP;
        var right = options.escFirst ? MOTOR_TEMP : ESC_TEMP;
        if (options.hides(left)) {
            eraseDraw(code, left, "label", 0x59);
            eraseDraw(code, left, "text", 0x91);
            options.appliedReadings |= left.bit;
        }
        if (options.hides(right)) {
            eraseDraw(code, right, "label", 0x6b);
            eraseDraw(code, right, "text", 0xbb);
            options.appliedReadings |= right.bit;
        }
        if (options.hides(left) || options.hides(right)) eraseDraw(code, left, "line", 0xd0);
    }

    private void swapReadingStrings(int code, int first, String firstText, int second, String secondText)
            throws IOException {
        if (!hasConstString(code, first, firstText) || !hasConstString(code, second, secondText)) {
            throw unsupportedCockpit("Temperatur-Reihenfolge");
        }
        int a = code + 18 + first * 2;
        int b = code + 18 + second * 2;
        int original = u16(a);
        writeShort(a, u16(b));
        writeShort(b, original);
    }

    private boolean hasConstString(int code, int position, String expected) {
        if (position + 2 > u32(code + 12)) return false;
        int offset = code + 16 + position * 2;
        int index = u16(offset + 2);
        return (u16(offset) & 0xff) == 0x1a && index < u32(56) && expected.equals(string(index));
    }

    private void eraseDraw(int code, CockpitReading reading, String name, int... positions) throws IOException {
        for (int position : positions) {
            int offset = instructionOffset(code, position, 3);
            int opcode = u16(offset) & 0xff;
            if ((opcode != 0x70 && opcode != 0x76) || !methodIs(u16(offset + 2), INSTRUMENT, name)) {
                throw unsupportedCockpit(reading.label);
            }
            eraseInvoke(offset);
        }
    }

    private void patchTripTarget(int code, DisplayPatch options) throws IOException {
        String marker = "cockpitTripReset";
        boolean newer = hasConstString(code, 0xd9, marker);
        if (!newer && !hasConstString(code, 0xcd, marker)) throw unsupportedCockpit(TRIP.label);
        String[] calls = {"setFocusable", "setContentDescription", "setOnClickListener"};
        int[] positions = newer ? new int[]{0xde, 0xe7, 0xef} : new int[]{0xd2, 0xd7, 0xdf};
        for (int i = 0; i < calls.length; i++) {
            int offset = instructionOffset(code, positions[i], 3);
            if (u16(offset) != 0x206e
                    || !methodIs(u16(offset + 2), "Landroid/widget/FrameLayout;", calls[i])) {
                throw unsupportedCockpit(TRIP.label);
            }
            eraseInvoke(offset);
        }
        options.applied |= DisplayPatch.TRIP_TARGET;
    }

    private void eraseInvoke(int offset) {
        for (int word = 0; word < 3; word++) writeShort(offset + 2 * word, 0);
    }

    private void patchInstrumentTap(int code, DisplayPatch options) throws IOException {
        if (!hasConstString(code, 0x22, "cockpitInstrument030")) return;
        for (int position = 0, units = u32(code + 12); position < units;) {
            int offset = code + 16 + 2 * position;
            int length = width(u16(offset), offset);
            if (length <= 0 || length > units - position) throw unsupportedCockpit("Cockpit-Tippschutz");
            if (length == 3 && u16(offset) == 0x206e
                    && methodIs(u16(offset + 2), INSTRUMENT, "setOnClickListener")) {
                eraseInvoke(offset);
                options.applied |= DisplayPatch.INSTRUMENT_TAP;
                return;
            }
            position += length;
        }
    }

    private void verifyHoldSettings(int code, String name, DisplayPatch options) throws IOException {
        if ("lambda$new$0".equals(name) && hasConstString(code, 0, "search")) {
            options.applied |= DisplayPatch.SEARCH_ACTION;
        }
        if (!"<init>".equals(name)) return;
        String frame = "Landroid/widget/FrameLayout;";
        String row = "Landroid/widget/LinearLayout;";
        boolean legacy = invokesAt(code, 0x48, 0x206e, 0x00a9, frame, "setOnClickListener")
            && invokesAt(code, 0x7d, 0x306e, 0x0b97, row, "addView")
            && invokesAt(code, 0x9a, 0x306e, 0x0975, frame, "addView");
        boolean current = invokesAt(code, 0x4c, 0x206e, 0x00a9, frame, "setOnClickListener")
            && invokesAt(code, 0x85, 0x306e, 0x0b97, row, "addView")
            && invokesAt(code, 0xa2, 0x306e, 0x0975, frame, "addView");
        if ((legacy || current) && hasConstString(code, 0x11, "fixedCockpit030")
                && hasConstString(code, 0x22, "cockpitInstrument030")
                && invokesAt(code, 0x2d, 0x306e, 0x0765, frame, "addView")) {
            options.applied |= DisplayPatch.SEARCH_TARGET;
        }
    }

    private boolean invokesAt(int code, int position, int opcode, int registers, String owner, String target) {
        if (position + 3 > u32(code + 12)) return false;
        int offset = code + 16 + position * 2;
        return u16(offset) == opcode && u16(offset + 4) == registers && methodIs(u16(offset + 2), owner, target);
    }

    private void verifyGridMethod(int index, String name, DisplayPatch display) {
        int protoId = u16(methods + 8 * index + 2);
        int proto = u32(76) + 12 * protoId;
        String shortType = string(u32(proto));
        if (name.equals("shownnumber") && shortType.equals("LL")) display.applied |= DisplayPatch.GRID_READING;
        if (name.equals("phasescale") && shortType.equals("DI")) display.applied |= DisplayPatch.GRID_SCALE;
        if (name.equals("decimal") && shortType.equals("LLI")) display.applied |= DisplayPatch.GRID_FORMAT;
    }

    private void verifyRestrictedAction(int code, String name, DisplayPatch display) {
        if ("rendervcuprofile".equals(name) && hasConstString(code, 0x6a, "Drosseln")
                && hasConstString(code, 0x50, "vcuProfile") && u32(code + 12) > 0x6a
                && u16(code + 16 + 2 * 0x2f) == 0x0212 && u16(code + 16 + 2 * 0x5b) == 0x2401) {
            display.applied |= DisplayPatch.RESTRICTED_MAPPING;
        }
        if (name.startsWith("lambda$rendervcuprofile$")
                && hasConstString(code, 0x14, "vcuProfileKnown")
                && hasConstString(code, 0x1f, "vcuProfileSwitchable")
                && hasConstString(code, 0x41, "vcuProfileSet")) {
            display.applied |= DisplayPatch.RESTRICTED_ACTION;
        }
    }

    private boolean methodIs(int index, String owner, String name) {
        if (index >= u32(88)) return false;
        int entry = methods + 8 * index;
        return owner.equals(string(u32(types + 4 * u16(entry))))
            && name.equals(string(u32(entry + 4)));
    }

    private static IOException unsupportedCockpit(String label) {
        return new IOException("In diesem Original-Build kann „" + label + "“ nicht angepasst werden.");
    }

    private void patchOnline(int code, String owner, String name, DisplayPatch options) throws IOException {
        for (OnlinePatch patch : OnlinePatch.values()) {
            if ((options.onlineMask & patch.bit) == 0
                    || !owner.endsWith("/" + patch.owner + ";") || !name.equals(patch.method)) continue;
            int count = u32(code + 12);
            if (count <= Math.max(patch.target, patch.position + 1)) throw patch.unsupported();
            int start = code + 16;
            int offset = start + 2 * patch.position;
            int target = start + 2 * patch.target;
            if (u16(offset) != patch.expected || u16(offset + 2) != patch.distance
                    || u16(target) != patch.targetInstruction) throw patch.unsupported();
            if (patch == OnlinePatch.UPDATES) {
                // Follow the existing “update unavailable” branch.
                writeShort(offset, 0);
                writeShort(offset + 2, 0);
            } else {
                // Preserve the method's existing failure or cleanup path.
                writeShort(offset, 0x0029);
            }
            options.onlineApplied |= patch.bit;
        }
    }

    private enum OnlinePatch {
        UPDATES(1, "MainActivity", "checkappupdate", 6, 0x0139, 3, 8, 0x2828, "Update-Prüfung"),
        ANALYTICS(2, "GtVescAnalytics", "real", 12, 0x0038, 10, 22, 0x0012, "Analyseversand"),
        COMMUNITY(4, "GtVescCommunityApi", "http", 9, 0x0038, 0x011a, 0x0123, 0x0422, "Community"),
        RANKING(8, "GtVescGpsSpeed", "syncranking", 5, 0x0038, 0x004f, 0x0054, 0x8207,
            "GPS-Bestwerte im Leaderboard");

        final int bit, position, expected, distance, target, targetInstruction;
        final String owner, method, label;

        OnlinePatch(int bit, String owner, String method, int position, int expected, int distance,
                    int target, int targetInstruction, String label) {
            this.bit = bit;
            this.owner = owner;
            this.method = method;
            this.position = position;
            this.expected = expected;
            this.distance = distance;
            this.target = target;
            this.targetInstruction = targetInstruction;
            this.label = label;
        }

        IOException unsupported() {
            return new IOException("In diesem Original-Build kann „" + label + "“ nicht blockiert werden.");
        }
    }

    private void patchDisplay(int code, String owner, String name, DisplayPatch display)
            throws IOException {
        if (MAIN_ACTIVITY.equals(owner) && display.hideStatusBar && "oncreate".equals(name)) {
            int addFlags = instructionOffset(code, 0x008e, 3);
            int method = methods + 8 * u16(addFlags + 2);
            int declaringType = u16(method);
            if (u16(addFlags) != 0x206e || u16(addFlags + 4) != 0x0010
                    || !"Landroid/view/Window;".equals(string(u32(types + 4 * declaringType)))
                    || !"addFlags".equals(string(u32(method + 4)))) {
                throw unsupportedDisplay();
            }
            // Preserve FLAG_KEEP_SCREEN_ON and add FLAG_FULLSCREEN.
            replaceImmediate(code, 0x008c, 0x0113, 0x0080, 0x0480);
            display.applied |= DisplayPatch.STATUS_BAR;
        }
        if (WORKSPACE.equals(owner) && display.hideReadingsPopup && "cockpitaction".equals(name)) {
            int action = instructionOffset(code, 0x002a, 2);
            int invoke = instructionOffset(code, 0x0032, 3);
            int method = methods + 8 * u16(invoke + 2);
            if (u16(action) != 0x001a || !"action/readings".equals(string(u16(action + 2)))
                    || u16(invoke) != 0x1070
                    || !WORKSPACE.equals(string(u32(types + 4 * u16(method))))
                    || !"showCockpitReadings".equals(string(u32(method + 4)))) {
                throw unsupportedDisplay();
            }
            // Leave the return and all other cockpit actions in place.
            for (int word = 0; word < 3; word++) writeShort(invoke + 2 * word, 0);
            display.applied |= DisplayPatch.READINGS_POPUP;
        }
        if (!display.compactNavigation) return;
        if (WORKSPACE.equals(owner) && "nav".equals(name)) {
            replaceImmediate(code, 0x0068, 0x0e15, 0x4110, 0x3f80); // Top padding: 9 to 1 dp.
            replaceImmediate(code, 0x0072, 0x0f15, 0x40e0, 0x3f80); // Bottom padding: 7 to 1 dp.
            replaceImmediate(code, 0x007b, 0x0c15, 0x4260, 0x41e0); // Minimum height: 56 to 28 dp.
            replaceImmediate(code, 0x00a6, 0x0e15, 0x41b0, 0x4140); // Icons: 22 to 12 dp.
            display.applied |= DisplayPatch.WORKSPACE_NAV;
        } else if (MAIN_ACTIVITY.equals(owner) && "bottomnavitem".equals(name)) {
            replaceInstruction(code, 0x000e, 0x4212, 0x2212);
            replaceInstruction(code, 0x001b, 0x2512, 0x1512);
            replaceImmediate(code, 0x0039, 0x0413, 30, 14);
            replaceImmediate(code, 0x0063, 0x0113, 24, 14);
            display.applied |= DisplayPatch.LEGACY_NAV_ITEM;
        } else if (MAIN_ACTIVITY.equals(owner) && "buildbottomnavigation".equals(name)) {
            replaceInstruction(code, 0x0014, 0x5412, 0x2412);
            display.applied |= DisplayPatch.LEGACY_NAV_ROW;
        }
    }

    private int instructionOffset(int code, int position, int length) throws IOException {
        if (position + length > u32(code + 12)) throw unsupportedDisplay();
        return code + 16 + position * 2;
    }

    private void replaceImmediate(int code, int position, int opcode, int expected, int replacement)
            throws IOException {
        int offset = instructionOffset(code, position, 2);
        if (u16(offset) != opcode || u16(offset + 2) != expected) throw unsupportedDisplay();
        writeShort(offset + 2, replacement);
    }

    private void replaceInstruction(int code, int position, int expected, int replacement)
            throws IOException {
        int offset = instructionOffset(code, position, 1);
        if (u16(offset) != expected) throw unsupportedDisplay();
        writeShort(offset, replacement);
    }

    private void writeShort(int offset, int value) {
        dex[offset] = (byte) value;
        dex[offset + 1] = (byte) (value >>> 8);
    }

    private static IOException unsupportedDisplay() {
        return new IOException("Diese Bridge-Version unterstützt die gewählte Anzeigeoption nicht.");
    }

    static final class DisplayPatch {
        static final int STATUS_BAR = 1;
        static final int WORKSPACE_NAV = 2;
        static final int LEGACY_NAV_ITEM = 4;
        static final int LEGACY_NAV_ROW = 8;
        static final int READINGS_POPUP = 16;
        static final int COCKPIT_TEXT = 32;
        static final int ESC_FIRST = 64;
        static final int TRIP_TARGET = 128;
        static final int SEARCH_ACTION = 256;
        static final int SEARCH_TARGET = 512;
        static final int GRID_TARGET = 1024;
        static final int GRID_READING = 2048;
        static final int GRID_SCALE = 4096;
        static final int GRID_FORMAT = 8192;
        static final int RESTRICTED_MAPPING = 16384;
        static final int RESTRICTED_ACTION = 32768;
        static final int INSTRUMENT_TAP = 65536;

        final boolean hideStatusBar;
        final boolean compactNavigation;
        final boolean hideReadingsPopup;
        final int onlineMask;
        final int cockpitTextPercent;
        final int hiddenReadings;
        final boolean escFirst;
        final boolean holdSettings;
        final boolean customGrid;
        final boolean tripTargetHidden;
        final boolean restrictedTripleTap;
        int appliedReadings;
        int applied;
        int onlineApplied;

        DisplayPatch(boolean hideStatusBar, boolean compactNavigation, boolean hideReadingsPopup,
                     int onlineMask, int cockpitTextPercent, int hiddenReadings, boolean escFirst,
                     boolean holdSettings, boolean customGrid, boolean restrictedTripleTap) {
            this.hideStatusBar = hideStatusBar;
            this.compactNavigation = compactNavigation;
            this.hideReadingsPopup = hideReadingsPopup;
            this.onlineMask = onlineMask;
            this.cockpitTextPercent = cockpitTextPercent;
            this.hiddenReadings = customGrid ? CockpitReading.allowedMask() : hiddenReadings;
            this.escFirst = escFirst;
            this.holdSettings = holdSettings;
            this.customGrid = customGrid;
            this.restrictedTripleTap = restrictedTripleTap;
            tripTargetHidden = TRIP.hiddenIn(hiddenReadings) && CONSUMPTION.hiddenIn(hiddenReadings);
        }

        boolean hides(CockpitReading reading) {
            return reading.hiddenIn(hiddenReadings);
        }

        void verify() throws IOException {
            int required = (hideStatusBar ? STATUS_BAR : 0)
                | (compactNavigation ? WORKSPACE_NAV | LEGACY_NAV_ITEM | LEGACY_NAV_ROW : 0)
                | (hideReadingsPopup ? READINGS_POPUP | INSTRUMENT_TAP : 0);
            if ((applied & required) != required) throw unsupportedDisplay();
            if (cockpitTextPercent != 100 && (applied & COCKPIT_TEXT) == 0) {
                throw unsupportedCockpit("Cockpit-Schrift");
            }
            for (CockpitReading reading : CockpitReading.values()) {
                if (hides(reading) && !reading.hiddenIn(appliedReadings)) throw unsupportedCockpit(reading.label);
            }
            if (escFirst && (applied & ESC_FIRST) == 0) throw unsupportedCockpit("Temperatur-Reihenfolge");
            if (holdSettings && (applied & (SEARCH_ACTION | SEARCH_TARGET)) != (SEARCH_ACTION | SEARCH_TARGET)) {
                throw unsupportedCockpit("Einstellungssuche per langem Druck");
            }
            int gridChecks = GRID_TARGET | GRID_READING | GRID_SCALE | GRID_FORMAT;
            if (customGrid && (applied & gridChecks) != gridChecks) throw unsupportedCockpit("Freie Reihenfolge");
            int profileChecks = RESTRICTED_MAPPING | RESTRICTED_ACTION;
            if (restrictedTripleTap && (applied & profileChecks) != profileChecks) {
                throw unsupportedCockpit("Dreifachtipp für das gedrosselte Profil");
            }
            if (tripTargetHidden && (applied & TRIP_TARGET) == 0) {
                throw unsupportedCockpit(TRIP.label);
            }
            for (OnlinePatch patch : OnlinePatch.values()) {
                if ((onlineMask & patch.bit) != 0 && (onlineApplied & patch.bit) == 0) throw patch.unsupported();
            }
        }
    }

    private int patchCode(int code, Map<Integer, Integer> colors) {
        int start = code + 16;
        int units = u32(code + 12);
        if (units < 0 || units > (dex.length - start) / 2) {
            throw new IllegalArgumentException("Ungültige DEX-Codegröße");
        }
        int changed = 0;
        for (int index = 0; index < units;) {
            int offset = start + index * 2;
            int instruction = u16(offset);
            int opcode = instruction & 0xff;
            int width = width(instruction, offset);
            if (width <= 0 || width > units - index) {
                throw new IllegalArgumentException("Unbekannte DEX-Anweisung");
            }
            if (opcode == 0x14) {
                Integer color = colors.get(u32(offset + 2));
                if (color != null) {
                    for (int part = 0; part < 4; part++) {
                        dex[offset + 2 + part] = (byte) (color >>> (8 * part));
                    }
                    changed++;
                }
            }
            index += width;
        }
        return changed;
    }

    private int width(int instruction, int offset) {
        int opcode = instruction & 0xff;
        if (opcode == 0 && instruction != 0) {
            switch (instruction) {
                case 0x0100: return 4 + u16(offset + 2) * 2;
                case 0x0200: return 2 + u16(offset + 2) * 4;
                case 0x0300:
                    long bytes = (long) u16(offset + 2) * Integer.toUnsignedLong(u32(offset + 4));
                    return bytes > dex.length ? -1 : 4 + (int) ((bytes + 1) / 2);
                default: return -1;
            }
        }
        if (opcode == 0x18) return 5;
        if (opcode == 0xfa || opcode == 0xfb) return 4;
        if (opcode == 0x03 || opcode == 0x06 || opcode == 0x09
                || opcode == 0x14 || opcode == 0x17 || opcode == 0x1b
                || (opcode >= 0x24 && opcode <= 0x26)
                || (opcode >= 0x2a && opcode <= 0x2c)
                || (opcode >= 0x6e && opcode <= 0x72)
                || (opcode >= 0x74 && opcode <= 0x78)
                || opcode == 0xfc || opcode == 0xfd) return 3;
        if (opcode == 0x02 || opcode == 0x05 || opcode == 0x08
                || opcode == 0x13 || (opcode >= 0x15 && opcode <= 0x16)
                || (opcode >= 0x19 && opcode <= 0x1a) || opcode == 0x1c
                || (opcode >= 0x1f && opcode <= 0x20)
                || (opcode >= 0x22 && opcode <= 0x23) || opcode == 0x29
                || (opcode >= 0x2d && opcode <= 0x3d)
                || (opcode >= 0x44 && opcode <= 0x6d)
                || (opcode >= 0x90 && opcode <= 0xaf)
                || (opcode >= 0xd0 && opcode <= 0xe2)
                || opcode >= 0xfe) return 2;
        if (opcode <= 0x12 || opcode == 0x1d || opcode == 0x1e
                || opcode == 0x21 || opcode == 0x27 || opcode == 0x28
                || (opcode >= 0x7b && opcode <= 0x8f)
                || (opcode >= 0xb0 && opcode <= 0xcf)) return 1;
        return -1;
    }

    private String string(int index) {
        Cursor cursor = new Cursor(u32(strings + index * 4));
        cursor.uleb();
        int end = cursor.position;
        while (dex[end] != 0) end++;
        return new String(dex, cursor.position, end - cursor.position, StandardCharsets.UTF_8);
    }

    private int u16(int offset) {
        return (dex[offset] & 0xff) | (dex[offset + 1] & 0xff) << 8;
    }

    private int u32(int offset) {
        return u16(offset) | u16(offset + 2) << 16;
    }

    private final class Cursor {
        int position;
        Cursor(int position) { this.position = position; }

        int uleb() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int next = dex[position++] & 0xff;
                value |= (next & 0x7f) << shift;
                if ((next & 0x80) == 0) return value;
            }
            throw new IllegalArgumentException("Ungültiger DEX-Index");
        }
    }
}
