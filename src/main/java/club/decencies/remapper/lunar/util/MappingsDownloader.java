package club.decencies.remapper.lunar.util;

import com.google.gson.*;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * TODO: experimental, some versions do not download.
 */
public class MappingsDownloader {

    private static final String ADDRESS = "http://export.mcpbot.golde.org/";

    private static final Map<String, String> STABLE_MAP = new HashMap<>();
    private static final Map<String, String> SNAPSHOT_MAP = new HashMap<>();

    public static void parseVersions() throws IOException {

        if (!STABLE_MAP.isEmpty()) return;

        final InputStream urlInputStream = getURLInputStream(new URL(ADDRESS + "versions.json"));
        final InputStreamReader inputStreamReader = new InputStreamReader(urlInputStream);
        final JsonObject object = new JsonParser().parse(inputStreamReader).getAsJsonObject();

        for (Map.Entry<String, JsonElement> stringJsonElementEntry : object.entrySet()) {
            JsonArray stable = stringJsonElementEntry.getValue().getAsJsonObject().get("stable").getAsJsonArray();
            if (stable.isEmpty()) {
                continue;
            }
            STABLE_MAP.put(stringJsonElementEntry.getKey(), String.valueOf(stable.get(0).getAsInt()));
        }

    }

    public static void download(String version, File destination) {
        try {
            parseVersions();

            String stableVersion = STABLE_MAP.get(version);

            if (stableVersion == null) {

            }

            String directoryPath = "mcp_stable/" + stableVersion + "-" + version + "/";
            String fileName = "mcp_stable-" + stableVersion + "-" + version + ".zip";

            String url = ADDRESS + directoryPath + fileName;

            File temp = File.createTempFile("mappings-" + version, "stable");
            temp.deleteOnExit();

            try (BufferedInputStream in = new BufferedInputStream(getURLInputStream(new URL(url))); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer, 0, 1024)) != -1) {
                    out.write(buffer, 0, read);
                }
                ZipFile zipFile = new ZipFile(temp);
                ZipEntry fields = zipFile.getEntry("fields.csv");
                ZipEntry methods = zipFile.getEntry("methods.csv");

                InputStream fieldsIn = zipFile.getInputStream(fields);

                FileOutputStream fieldsFos = new FileOutputStream(new File(destination, "fields.csv"));

                while ((read = fieldsIn.read(buffer, 0, 1024)) != -1) {
                    fieldsFos.write(buffer, 0, read);
                }

                InputStream methodsIn = zipFile.getInputStream(methods);

                FileOutputStream methodsFos = new FileOutputStream(new File(destination, "methods.csv"));

                while ((read = methodsIn.read(buffer, 0, 1024)) != -1) {
                    methodsFos.write(buffer, 0, read);
                }
            }

            File srgTemp = File.createTempFile("srg-" + version, "stable");
            srgTemp.deleteOnExit();

            String srgUrl = ADDRESS + "mcp/" + version + "/mcp-" + version + "-srg.zip";

            try (BufferedInputStream in = new BufferedInputStream(getURLInputStream(new URL(srgUrl))); FileOutputStream out = new FileOutputStream(srgTemp)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer, 0, 1024)) != -1) {
                    out.write(buffer, 0, read);
                }
                ZipFile zipFile = new ZipFile(srgTemp);
                ZipEntry joined = zipFile.getEntry("joined.srg");

                InputStream joinedIn = zipFile.getInputStream(joined);

                FileOutputStream joinedFos = new FileOutputStream(new File(destination, "joined.srg"));

                while ((read = joinedIn.read(buffer, 0, 1024)) != -1) {
                    joinedFos.write(buffer, 0, read);
                }
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static InputStream getURLInputStream(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        connection.connect();
        return connection.getInputStream();
    }


}
