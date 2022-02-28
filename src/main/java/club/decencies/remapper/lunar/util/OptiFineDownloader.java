package club.decencies.remapper.lunar.util;

import lombok.SneakyThrows;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptiFineDownloader {

    private static final String ADDRESS = "https://optifine.net/";

    // todo refactor..
    @SneakyThrows
    public static void download(String version, File destination, Consumer<JarFile> consumer) {
        if (!version.contains("preview_") && version.contains("pre")) {
            version = "preview_".concat(version);
        }
        if (destination.exists() && destination.length() > 0) {
            consumer.accept(new JarFile(destination));
            return;
        } else {
            destination.getParentFile().mkdirs();
            destination.createNewFile();
        }
        if (version.contains("&x=")) {
            System.out.println(version);
            try (BufferedInputStream in = new BufferedInputStream(getURLInputStream(new URL(version))); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer, 0, 1024)) != -1) {
                    out.write(buffer, 0, read);
                }
                consumer.accept(new JarFile(destination));
            }
        } else {
            try (Scanner scanner = new Scanner(getURLInputStream(new URL(ADDRESS + "adloadx?f=" + version)), StandardCharsets.UTF_8.toString())) {
                scanner.useDelimiter("\\A");
                String contents = scanner.hasNext() ? scanner.next() : "";
                Pattern pattern = Pattern.compile(Pattern.quote(version) + "&x=([a-z0-9]{32})");
                final Matcher matcher = pattern.matcher(contents);
                if (matcher.find()) {
                    final String key = matcher.group(1);
                    download(ADDRESS + "downloadx?f=" + version + ".jar&x=" + key, destination, consumer);
                }
            }
        }
    }

    private static InputStream getURLInputStream(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        connection.connect();
        return connection.getInputStream();
    }

}
