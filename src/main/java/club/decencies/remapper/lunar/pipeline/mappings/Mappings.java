package club.decencies.remapper.lunar.pipeline.mappings;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Mappings {

    /**
     * The type of the mappings file.
     */
    public enum MappingType {

        // 1.13+ MAPPINGS
        MOJANG,

        // SEARGE MAPPINGS
        SEARGE,

        // SEARGE CSV FILES
        FIELDS,
        METHODS,
        PARAMS
    }

    private final MappingType type;

    public static MappingType getType(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            switch (fileInputStream.read()) {
                case '#': // Microsoft copyright header.
                    return MappingType.MOJANG;
                case 'P': // PK . net/minecraft/src
                    return MappingType.SEARGE;
                case 's': // searge
                    byte[] buffer = new byte[100];
                    fileInputStream.read(buffer);
                    String s = new String(buffer);
                    return s.contains("field_") ? MappingType.FIELDS : (s.contains("func_") ? MappingType.PARAMS : null);
            }
            throw new RuntimeException(String.format("Modified or unrecognised mapping file (%s)", file.getAbsolutePath()));
        }
    }

    public static Mappings from(File directory) throws IOException {

        if (!directory.isDirectory()) {
            throw new RuntimeException(directory.getAbsolutePath() + " is not a directory.");
        }

        final File[] files = directory.listFiles();

        if (files == null) {
            throw new RuntimeException(directory.getAbsolutePath() + " is empty.");
        }

        for (final File file : files) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header.startsWith("#"));
            }
        }

        return new Mappings(MappingType.SEARGE);
    }

}
