package club.decencies.remapper.lunar.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/**
 * @author Decencies
 * @since 29/06/2021 13:04
 */
public class FileUtil {

    public static void walk(File directory, BiConsumer<File, File> consumer) {
        for (File child : Objects.requireNonNull(directory.listFiles())) {
            if (child.isDirectory()) {
                for (File file : Objects.requireNonNull(child.listFiles())) {
                    consumer.accept(child, file);
                }
            }
        }
    }

    public static <T, G> void zips(File directory, G global, Function<String, T> directoryMapper, BiConsumer<T, ZipFile> consumer, BiConsumer<T, G> processor) throws IOException {
        final Collection<T> ts = new ArrayList<>();
        for (File child : Objects.requireNonNull(directory.listFiles())) {
            if (child.isDirectory()) {
                final T t = directoryMapper.apply(child.getName());
                for (File file : Arrays.stream(Objects.requireNonNull(child.listFiles())).filter(f -> !f.isDirectory()).collect(Collectors.toList())) {
                    consumer.accept(t, new ZipFile(file));
                }
                ts.add(t);
            }
        }
        ts.forEach(r -> processor.accept(r, global));
    }

    public static Collection<File> walk(File directory, String filter) {
        return walk(directory, filter, new ArrayList<>());
    }

    private static Collection<File> walk(File dir, String filter, Collection<File> current) {
        File[] files;
        if (dir.isDirectory() && (files = Objects.requireNonNull(dir.listFiles())).length > 0)
            for (File file : files) walk(file, filter, current);
        else if (!dir.isDirectory() && dir.getName().endsWith(filter))
            current.add(dir);
        return current;
    }

}
