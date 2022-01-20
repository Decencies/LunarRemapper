package club.decencies.remapper.lunar;

import club.decencies.remapper.lunar.mappings.*;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.TransformationMappings;
import club.decencies.remapper.lunar.pipeline.mixin.MixinClassProcessor;

import club.decencies.remapper.lunar.util.ClassUtil;
import club.decencies.remapper.lunar.util.FileUtil;

import club.decencies.remapper.lunar.util.OptiFineDownloader;

import com.nothome.delta.GDiffPatcher;

import io.sigpipe.jbsdiff.Patch;

import me.tongfei.progressbar.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.*;

import java.io.*;

import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Pattern COMMIT_PATTERN = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<major>[0-9])\\.(?<minor>[0-9]{1,2})(\\.(?<patch>[0-9]{1,2}))?");
    private static final Map<String, MappingProvider> PROVIDERS = new HashMap<>();

    private static final GDiffPatcher PATCHER = new GDiffPatcher();

    public static void main(String[] args) throws IOException {

        System.out.println(readLocal("icon").replace("*", readLocal("version")));

        FileUtil.walk(new File("./mappings"), (directory, file) -> {
            if (file.getName().endsWith(".txt")) {
                PROVIDERS.put(directory.getName(), new MojMap(file));
            } else if (file.getName().endsWith(".srg")) {
                PROVIDERS.put(directory.getName(), new SrgMap(file));
            } else if (file.getName().endsWith(".csv")) {
                final MappingProvider mappingProvider = PROVIDERS.get(directory.getName());
                if (file.getName().startsWith("fields")) {
                    mappingProvider.pushFields(file);
                } else if (file.getName().startsWith("methods")) {
                    mappingProvider.pushMethods(file);
                } else if (file.getName().startsWith("params")) {
                    mappingProvider.pushParameters(file);
                }
            }
        });

        FileUtil.walk(new File("./input"), ".jar").forEach(lunarFile -> {
            if (!lunarFile.getName().contains("lunar-prod")) return;
            LunarVersion lunarVersion = new LunarVersion();
            MappingProvider mappingProvider = null;
            try (JarFile lunarJar = new JarFile(lunarFile)) {
                for (Enumeration<JarEntry> entries = lunarJar.entries(); entries.hasMoreElements(); ) {
                    JarEntry entry = entries.nextElement();
                    InputStream inputStream = lunarJar.getInputStream(entry);
                    if (entry.getName().endsWith(".class") || entry.getName().endsWith(".lclass")) {
                        ClassReader reader = new ClassReader(inputStream);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0);

                        if (classNode.name.endsWith("Config")) {
                            classNode.fields.stream().filter(fd -> fd.name.equals("VERSION")).findFirst()
                                    .map(fd -> (String) fd.value)
                                    .ifPresent(release -> {
                                        OptiFineDownloader.download(release, new File("./optifine/" + release + ".jar"), (optifine) -> {
                                            LOGGER.info(String.format("Loaded OptiFine version %s.", release));
                                            optifine.stream().filter(e -> e.getName().endsWith(".xdelta")).forEach(patch -> {
                                                try {
                                                    final InputStream patchInputStream = optifine.getInputStream(patch);
                                                    final String name = patch.getName().substring(patch.getName().lastIndexOf('/') + 1, patch.getName().indexOf('.'));
                                                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                                                    final byte[] buffer = new byte[1024];
                                                    int length;
                                                    while ((length = patchInputStream.read(buffer)) != -1) {
                                                        out.write(buffer, 0, length);
                                                    }
                                                    final byte[] patchBytes = out.toByteArray();
                                                    lunarVersion.addOptiFinePatch(name, patchBytes);
                                                } catch (IOException exception) {
                                                    exception.printStackTrace();
                                                }
                                            });
                                        });
                                    });
//                            classNode.fields.stream().filter(fd -> fd.name.equals("MC_VERSION")).findFirst()
//                                    .map(fd -> (String) fd.value)
//                                    .ifPresent(mcVersion -> {
//                                        if (!lunarVersion.hasVerBeenSet()) {
//                                            Matcher matcher = VERSION_PATTERN.matcher(entry.getName());
//                                            if (matcher.find()) {
//                                                String mcVer = matcher.group();
//                                                if (!hasMc(mcVer)) {
//                                                    LOGGER.warn(String.format("Missing Minecraft version %s; loading anyway.", mcVer));
//                                                } else {
//                                                    if (!PROVIDERS.containsKey(mcVer)) {
//                                                        LOGGER.warn(String.format("Missing mappings for Minecraft %s.", mcVer));
//                                                    } else {
//                                                        mappingProvider = PROVIDERS.get(mcVer);
//                                                    }
//                                                    LOGGER.info(String.format("Loading Lunar Client for Minecraft %s.", mcVer));
//                                                }
//                                                lunarVersion.setMcVer(matcher.group());
//                                            }
//                                        }
//                                    });
                        }

                        lunarVersion.addClass(classNode.name, classNode);
                        readCommit(classNode).ifPresent(lunarVersion::setCommit);
                    } else {
                        if (!lunarVersion.hasVerBeenSet()) {
                            // match <major>.<minor>.<patch>.json (legacy lunar versions)
                            Matcher matcher = VERSION_PATTERN.matcher(entry.getName());
                            if (matcher.find()) {
                                String mcVer = matcher.group();
                                if (!hasMc(mcVer)) {
                                    LOGGER.warn(String.format("Missing Minecraft version %s; loading anyway.", mcVer));
                                } else {
                                    if (!PROVIDERS.containsKey(mcVer)) {
                                        LOGGER.warn(String.format("Missing mappings for Minecraft %s.", mcVer));
                                    } else {
                                        mappingProvider = PROVIDERS.get(mcVer);
                                    }
                                    LOGGER.info(String.format("Loading Lunar Client for Minecraft %s.", mcVer));
                                }
                                lunarVersion.setMcVer(matcher.group());
                            }
                        }
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) != -1) {
                            stream.write(buffer, 0, length);
                        }
                        byte[] bytes = lunarVersion.addResource(entry.getName(), stream.toByteArray());
                        if (entry.getName().startsWith("patch/")) {
                            if (entry.getName().endsWith(".txt")) {
                                final List<String> lines = Arrays.stream(new String(bytes).split("\n")).collect(Collectors.toList());
                                if (entry.getName().endsWith("mappings.txt")) {
                                    for (String line : lines) {
                                        final String[] split = line.split(" ");
                                        lunarVersion.addMapping(split[0].replace(".", "/"), split[1]);
                                    }
                                } else if (entry.getName().endsWith("patches.txt")) {
                                    for (final String line : lines) {
                                        final int space = line.indexOf(32);
                                        lunarVersion.addLunarPatch(line.substring(0, space).replace(".", "/"), Base64.getDecoder().decode(line.substring(space + 1)));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            if (lunarVersion.hasVerBeenSet()) {
                if (hasMc(lunarVersion.getMcVer())) {
                    String mcVer = lunarVersion.getMcVer();
                    File minecraftFile = new File(getMinecraftDirectory(), "versions/" + mcVer + "/" + mcVer + ".jar");
                    try (JarFile minecraftJar = new JarFile(minecraftFile)) {
                        ProgressBar.wrap(minecraftJar.stream().filter(e -> e.getName().endsWith(".class")).collect(Collectors.toList()), "Patching Classes").forEach(entry -> {
                            try {
                                InputStream inputStream = minecraftJar.getInputStream(entry);
                                if (entry.getName().endsWith(".class")) {

                                    ByteArrayOutputStream classStream = new ByteArrayOutputStream();
                                    byte[] buffer = new byte[1024];
                                    int length;
                                    while ((length = inputStream.read(buffer)) != -1) {
                                        classStream.write(buffer, 0, length);
                                    }

                                    byte[] classBytes = classStream.toByteArray();


                                    // todo possible mapping checks etc. here
                                    ClassReader originalReader = new ClassReader(classBytes);
                                    ClassNode originalClassNode = new ClassNode();
                                    originalReader.accept(originalClassNode, 0);

                                    Optional<byte[]> optiFinePatch = lunarVersion.getOptiFinePatch(originalClassNode.name);

                                    if (optiFinePatch.isPresent()) {

                                        final byte[] bytes = optiFinePatch.get();

                                        // add EOF byte to array.
                                        final byte[] fixedPatchBytes = new byte[bytes.length + 1];
                                        System.arraycopy(bytes, 0, fixedPatchBytes, 0, bytes.length);
                                        fixedPatchBytes[fixedPatchBytes.length - 1] = 0;

                                        //LOGGER.info(String.format("Patching OptiFine Minecraft class %s for %s.", originalClassNode.name, mcVer));
                                        try {
                                            byte[] newBytes = PATCHER.patch(classBytes, fixedPatchBytes);
                                            ClassReader optiFinePatchReader = new ClassReader(newBytes);
                                            originalClassNode = new ClassNode();
                                            optiFinePatchReader.accept(originalClassNode, 0);
                                        } catch (Exception exception) {
                                            LOGGER.error(String.format("Failed to patch %s", originalClassNode.name));
                                        }
                                    }

                                    lunarVersion.addMcClass(originalClassNode.name, originalClassNode);

                                    Optional<byte[]> lunarPatch = lunarVersion.getLunarPatch(originalClassNode.name);
                                    // todo possible warning if patch isn't present...?
                                    if (lunarPatch.isPresent()) {

                                        //LOGGER.debug(String.format("Patching Lunar Minecraft class %s for %s.", originalClassNode.name, mcVer));

                                        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                        Patch.patch(classBytes, lunarPatch.get(), stream);
                                        final byte[] byteArray = stream.toByteArray();
                                        stream.close();

                                        ClassReader patchedReader = new ClassReader(byteArray);
                                        ClassNode patchedClassNode = new ClassNode();
                                        patchedReader.accept(patchedClassNode, 0);

                                        lunarVersion.addPatchedMcClass(patchedClassNode.name, patchedClassNode);

                                    }
                                }
                            } catch (Exception exception) {
                                exception.printStackTrace();
                            }
                        });
//                        for (Enumeration<JarEntry> entries = minecraftJar.entries(); entries.hasMoreElements(); ) {
//                            JarEntry entry = entries.nextElement();
//                            InputStream inputStream = minecraftJar.getInputStream(entry);
//                            if (entry.getName().endsWith(".class")) {
//                                minecraftClassesTotal++;
//
//                                ByteArrayOutputStream classStream = new ByteArrayOutputStream();
//                                byte[] buffer = new byte[1024];
//                                int length;
//                                while ((length = inputStream.read(buffer)) != -1) {
//                                    classStream.write(buffer, 0, length);
//                                }
//
//                                byte[] classBytes = classStream.toByteArray();
//
//
//                                // todo possible mapping checks etc. here
//                                ClassReader originalReader = new ClassReader(classBytes);
//                                ClassNode originalClassNode = new ClassNode();
//                                originalReader.accept(originalClassNode, 0);
//
//                                Optional<byte[]> optiFinePatch = lunarVersion.getOptiFinePatch(originalClassNode.name);
//
//                                if (optiFinePatch.isPresent()) {
//
//                                    final byte[] bytes = optiFinePatch.get();
//
//                                    // add EOF byte to array.
//                                    final byte[] fixedPatchBytes = new byte[bytes.length + 1];
//                                    System.arraycopy(bytes, 0, fixedPatchBytes, 0, bytes.length);
//                                    fixedPatchBytes[fixedPatchBytes.length - 1] = 0;
//
//                                    //LOGGER.info(String.format("Patching OptiFine Minecraft class %s for %s.", originalClassNode.name, mcVer));
//                                    try {
//                                        byte[] newBytes = PATCHER.patch(classBytes, fixedPatchBytes);
//                                        ClassReader optiFinePatchReader = new ClassReader(newBytes);
//                                        originalClassNode = new ClassNode();
//                                        optiFinePatchReader.accept(originalClassNode, 0);
//                                    } catch (Exception exception) {
//                                        LOGGER.error(String.format("Failed to patch %s", originalClassNode.name));
//                                    }
//                                }
//
//                                lunarVersion.addMcClass(originalClassNode.name, originalClassNode);
//
//                                Optional<byte[]> lunarPatch = lunarVersion.getLunarPatch(originalClassNode.name);
//                                // todo possible warning if patch isn't present...?
//                                if (lunarPatch.isPresent()) {
//
//                                    //LOGGER.debug(String.format("Patching Lunar Minecraft class %s for %s.", originalClassNode.name, mcVer));
//
//                                    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                                    Patch.patch(classBytes, lunarPatch.get(), stream);
//                                    final byte[] byteArray = stream.toByteArray();
//                                    stream.close();
//
//                                    ClassReader patchedReader = new ClassReader(byteArray);
//                                    ClassNode patchedClassNode = new ClassNode();
//                                    patchedReader.accept(patchedClassNode, 0);
//
//                                    lunarVersion.addPatchedMcClass(patchedClassNode.name, patchedClassNode);
//
//                                    patchesApplied++;
//                                }
//                            }
//                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }

                    // final Mappings mappings = new Mappings(lunarVersion, mappingProvider);

                    //  MappingProvider finalMappingProvider = mappingProvider;

                    TransformationMappings transformationMappings = new TransformationMappings(lunarVersion, mappingProvider);
                    MixinClassProcessor mixinClassProcessor = new MixinClassProcessor(lunarVersion, mappingProvider);

                    ProgressBar.wrap(lunarVersion.getPatchedMcClasses().entrySet(), "Resolving Classes").forEach((entry) -> {
                        String name = entry.getKey();
                        ClassNode patched = entry.getValue();
                        lunarVersion.getMapping(name).flatMap(lunarVersion::getMcClass).ifPresent(original -> {
                            mixinClassProcessor.process(patched, original, transformationMappings);
                        });
                    });

//                    lunarVersion.getPatchedMcClasses().forEach((name, patched) -> {
//                        lunarVersion.getMapping(name).flatMap(lunarVersion::getMcClass).ifPresent(original -> {
//                            mixinClassProcessor.process(patched, original, transformationMappings);
////                            mixinClassProcessor.resolve(lunarVersion, finalMappingProvider, patched, original, mixinNameMap);
////                            mappings.process(original, patched);
//                        });
//                    });

                    mixinClassProcessor.postTransform();

                    Map<String, String> mixinNameMap = transformationMappings.getClassRenameMap();

                    File exportDir = new File(new File("."), String.format("export/%s/", mcVer));
                    if (exportDir.exists() || exportDir.mkdirs()) {
                        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(new File(exportDir, "lunar.jar")))) {
                            for (ClassNode classNode : lunarVersion.getClasses()) {
                                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                                classNode.accept(new ClassRemapper(writer, transformationMappings));

                                if (mixinNameMap.containsKey(classNode.name)) {
                                    out.putNextEntry(new JarEntry(mixinNameMap.get(classNode.name) + ".class"));
                                } else {
                                    out.putNextEntry(new JarEntry(classNode.name + ".class"));
                                }
                                out.write(writer.toByteArray());
                            }
                            for (Map.Entry<String, ClassNode> entry : lunarVersion.getPatchedMcClasses().entrySet()) {
                                ClassNode classNode = entry.getValue();
                                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                                classNode.accept(writer);
                                out.putNextEntry(new JarEntry(classNode.name + ".class"));
                                out.write(writer.toByteArray());
                            }
                            for (Map.Entry<String, byte[]> entry : lunarVersion.getResources().entrySet()) {
                                String resourceName = entry.getKey();
                                byte[] resourceData = entry.getValue();
                                out.putNextEntry(new ZipEntry(resourceName));
                                out.write(resourceData);
                            }
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                        try (PrintStream stream = new PrintStream(new FileOutputStream(new File(exportDir, "map.txt")))) {
                            lunarVersion.getExports().forEach((oldName, newName) -> stream.println(oldName + ' ' + newName));
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    } else {
                        LOGGER.error(String.format("Failed to create export directory for %s.", mcVer));
                    }
                }
            }
        });
    }

    private static Optional<String> readCommit(ClassNode node) {
        if (ClassUtil.isFlag(node.access, Opcodes.ACC_PUBLIC, Opcodes.ACC_FINAL)) {
            Optional<FieldNode> fieldNode = node.fields
                    .stream()
                    .filter(f ->
                            ClassUtil.isFlag(
                                    f.access,
                                    Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC, Opcodes.ACC_FINAL
                            ) && f.desc.equals("Ljava/lang/String;") && f.value != null && COMMIT_PATTERN.matcher(((String) f.value)).matches())
                    .findAny();
            if (fieldNode.isPresent()) {
                return Optional.of((String) fieldNode.get().value);
            }
        }
        return Optional.empty();
    }

    private static boolean hasMc(String mcVer) {
        return new File(getMinecraftDirectory(), "versions/" + mcVer + "/" + mcVer + ".jar").exists();
    }

    private static File getMinecraftDirectory() {
        final String os;
        if ((os = System.getProperty("os.name").toLowerCase()).contains("win"))
            return new File(new File(System.getenv("APPDATA")), ".minecraft");
        if (os.contains("mac"))
            return new File(new File(System.getProperty("user.home")), "Library/Application Support/minecraft");
        if (os.contains("linux"))
            return new File(new File(System.getProperty("user.home")), ".minecraft/");
        throw new RuntimeException("Failed to determine Minecraft directory for OS: " + os);
    }

    private static String readLocal(String name) {
        try {
            InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(name);
            if (inputStream != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    stream.write(buffer, 0, length);
                }
                return stream.toString();
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }

}
