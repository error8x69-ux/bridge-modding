package com.gtbridge;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class OriginalBridgeUpdates {
    public static final String ORIGINAL = "de.smartrepair.gtvescbridge";
    public static final String CERTIFICATE =
        "d6a08b2b9b7103e99b2938d1fa8359c3aa0c331d2f396c6f58d6601026d19dc1";
    public static final String PATCHER_PACKAGE = "com.gtbridge.neonpatcher";
    public static final String OPEN_UPDATE = PATCHER_PACKAGE + ".CLONE_UPDATE";
    private static final String HOST = "api.smart-r.uk";
    private static final String FEED = "https://" + HOST + "/gt_vesc/app/latest.json";
    private static final long MAX_APK_SIZE = 128L * 1024 * 1024;

    private OriginalBridgeUpdates() {}

    public static final class Release {
        public final long code;
        public final String name;
        public final String changelog;
        private final URI url;
        private final long size;
        private final String sha256;

        private Release(long code, String name, String changelog, URI url, long size, String sha256) {
            this.code = code;
            this.name = name;
            this.changelog = changelog;
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public static Release latest() throws Exception {
        HttpURLConnection connection = connect(URI.create(FEED));
        try (InputStream input = response(connection);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            copy(input, bytes, 64 * 1024, null);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!json.getBoolean("available")) return null;
            if (!"GT_VESC".equals(json.getString("family")) || json.getInt("schemaVersion") != 1
                    || !ORIGINAL.equals(json.getString("packageName"))) {
                throw new IOException("Die Update-Quelle meldet ein anderes Produkt.");
            }
            URI url = new URI(json.getString("apk_url"));
            String sha256 = json.getString("sha256").toLowerCase(Locale.ROOT);
            long code = json.getLong("versionCode");
            long size = json.getLong("size");
            String name = json.getString("versionName");
            if (!CERTIFICATE.equalsIgnoreCase(json.getString("signingCertificateSha256"))
                    || !sha256.matches("[a-f0-9]{64}") || code <= 0 || size <= 0 || size > MAX_APK_SIZE
                    || name.trim().isEmpty() || name.length() > 80
                    || !"https".equals(url.getScheme()) || !HOST.equalsIgnoreCase(url.getHost())
                    || url.getUserInfo() != null || url.getPort() != -1
                    || url.getRawQuery() != null || url.getRawFragment() != null
                    || !url.getRawPath().matches("/gt_vesc/app/releases/[A-Za-z0-9][A-Za-z0-9_.-]*\\.apk")) {
                throw new IOException("Die Update-Quelle meldet eine ungültige Original-APK.");
            }
            Object changelog = json.opt("changelog");
            String notes = changelog instanceof String ? ((String) changelog).trim() : "";
            return new Release(code, name, notes, url, size, sha256);
        } finally {
            connection.disconnect();
        }
    }

    public static void download(Release release, File apk) throws Exception {
        HttpURLConnection connection = connect(release.url);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = response(connection); OutputStream output = new FileOutputStream(apk)) {
            long size = copy(input, output, release.size, digest);
            if (size != release.size || !release.sha256.equals(hex(digest.digest()))) {
                throw new IOException("Die heruntergeladene Original-APK stimmt nicht mit der Update-Quelle überein.");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection connect(URI url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Bridge-Modding");
        return connection;
    }

    private static InputStream response(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) throw new IOException("Update-Quelle antwortet mit HTTP " + code + ".");
        return connection.getInputStream();
    }

    private static long copy(InputStream input, OutputStream output, long limit, MessageDigest digest)
            throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            total += count;
            if (total > limit) throw new IOException("Die Update-Datei ist größer als erwartet.");
            output.write(buffer, 0, count);
            if (digest != null) digest.update(buffer, 0, count);
        }
        return total;
    }

    private static String hex(byte[] data) {
        StringBuilder value = new StringBuilder(data.length * 2);
        for (byte item : data) {
            value.append(Character.forDigit(item >>> 4 & 15, 16));
            value.append(Character.forDigit(item & 15, 16));
        }
        return value.toString();
    }
}
