package club.decencies.remapper.lunar.pipeline.type;

import org.objectweb.asm.*;

public class TweakerGenerator implements Opcodes {

    public static byte[] generate(String className, String mixinConfigFile, boolean usingOptiFine) {

        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", new String[]{"net/minecraft/launchwrapper/ITweaker"});

        classWriter.visitInnerClass("org/spongepowered/asm/mixin/MixinEnvironment$Side", "org/spongepowered/asm/mixin/MixinEnvironment", "Side", ACC_PUBLIC | ACC_STATIC | ACC_ENUM | ACC_ABSTRACT);

        {
            fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "args", "Ljava/util/ArrayList;", "Ljava/util/ArrayList<Ljava/lang/String;>;", null);
            fieldVisitor.visitEnd();
        }
        {
            fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "isRunningOptifine", "Z", null, usingOptiFine);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(13, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(15, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            methodVisitor.visitFieldInsn(PUTFIELD, className, "args", "Ljava/util/ArrayList;");
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(16, label2);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, className, "isRunningOptifine", "Z");
            methodVisitor.visitInsn(RETURN);
            Label label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label3, 0);
            methodVisitor.visitMaxs(3, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "acceptOptions", "(Ljava/util/List;Ljava/io/File;Ljava/io/File;Ljava/lang/String;)V", "(Ljava/util/List<Ljava/lang/String;>;Ljava/io/File;Ljava/io/File;Ljava/lang/String;)V", null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(20, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, className, "args", "Ljava/util/ArrayList;");
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "addAll", "(Ljava/util/Collection;)Z", false);
            methodVisitor.visitInsn(POP);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(22, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitLdcInsn("gameDir");
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, className, "addArg", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(23, label2);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitLdcInsn("assetsDir");
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, className, "addArg", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
            Label label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLineNumber(24, label3);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitLdcInsn("version");
            methodVisitor.visitVarInsn(ALOAD, 4);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, className, "addArg", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
            Label label4 = new Label();
            methodVisitor.visitLabel(label4);
            methodVisitor.visitLineNumber(25, label4);
            methodVisitor.visitInsn(RETURN);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label5, 0);
            methodVisitor.visitLocalVariable("args", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", label0, label5, 1);
            methodVisitor.visitLocalVariable("gameDir", "Ljava/io/File;", null, label0, label5, 2);
            methodVisitor.visitLocalVariable("assetsDir", "Ljava/io/File;", null, label0, label5, 3);
            methodVisitor.visitLocalVariable("profile", "Ljava/lang/String;", null, label0, label5, 4);
            methodVisitor.visitMaxs(3, 5);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "injectIntoClassLoader", "(Lnet/minecraft/launchwrapper/LaunchClassLoader;)V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(29, label0);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/spongepowered/asm/launch/MixinBootstrap", "init", "()V", false);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(31, label1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/spongepowered/asm/mixin/MixinEnvironment", "getDefaultEnvironment", "()Lorg/spongepowered/asm/mixin/MixinEnvironment;", false);
            methodVisitor.visitVarInsn(ASTORE, 2);
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(32, label2);
            methodVisitor.visitLdcInsn(mixinConfigFile);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/spongepowered/asm/mixin/Mixins", "addConfiguration", "(Ljava/lang/String;)V", false);
            Label label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLineNumber(34, label3);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/MixinEnvironment", "getObfuscationContext", "()Ljava/lang/String;", false);
            Label label4 = new Label();
            methodVisitor.visitJumpInsn(IFNONNULL, label4);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitLineNumber(35, label5);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitLdcInsn("notch");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/MixinEnvironment", "setObfuscationContext", "(Ljava/lang/String;)V", false);
            methodVisitor.visitLabel(label4);
            methodVisitor.visitLineNumber(38, label4);
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"org/spongepowered/asm/mixin/MixinEnvironment"}, 0, null);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitFieldInsn(GETSTATIC, "org/spongepowered/asm/mixin/MixinEnvironment$Side", "CLIENT", "Lorg/spongepowered/asm/mixin/MixinEnvironment$Side;");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/MixinEnvironment", "setSide", "(Lorg/spongepowered/asm/mixin/MixinEnvironment$Side;)Lorg/spongepowered/asm/mixin/MixinEnvironment;", false);
            methodVisitor.visitInsn(POP);
            Label label6 = new Label();
            methodVisitor.visitLabel(label6);
            methodVisitor.visitLineNumber(40, label6);
            methodVisitor.visitInsn(RETURN);
            Label label7 = new Label();
            methodVisitor.visitLabel(label7);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label7, 0);
            methodVisitor.visitLocalVariable("classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;", null, label0, label7, 1);
            methodVisitor.visitLocalVariable("environment", "Lorg/spongepowered/asm/mixin/MixinEnvironment;", null, label2, label7, 2);
            methodVisitor.visitMaxs(2, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getLaunchTarget", "()Ljava/lang/String;", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(43, label0);
            methodVisitor.visitLdcInsn("net.minecraft.client.main.Main");
            methodVisitor.visitInsn(ARETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getLaunchArguments", "()[Ljava/lang/String;", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(47, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, className, "args", "Ljava/util/ArrayList;");
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/String");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", false);
            methodVisitor.visitTypeInsn(CHECKCAST, "[Ljava/lang/String;");
            methodVisitor.visitInsn(ARETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label1, 0);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "addArg", "(Ljava/lang/String;Ljava/lang/Object;)V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(51, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, className, "args", "Ljava/util/ArrayList;");
            methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
            methodVisitor.visitLdcInsn("--");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
            methodVisitor.visitInsn(POP);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(52, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, className, "args", "Ljava/util/ArrayList;");
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(INSTANCEOF, "java/lang/String");
            Label label2 = new Label();
            methodVisitor.visitJumpInsn(IFEQ, label2);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            Label label3 = new Label();
            methodVisitor.visitJumpInsn(GOTO, label3);
            methodVisitor.visitLabel(label2);
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/util/ArrayList"});
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(INSTANCEOF, "java/io/File");
            Label label4 = new Label();
            methodVisitor.visitJumpInsn(IFEQ, label4);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/io/File");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false);
            methodVisitor.visitJumpInsn(GOTO, label3);
            methodVisitor.visitLabel(label4);
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/util/ArrayList"});
            methodVisitor.visitLdcInsn(".");
            methodVisitor.visitLabel(label3);
            methodVisitor.visitFrame(Opcodes.F_FULL, 3, new Object[]{className, "java/lang/String", "java/lang/Object"}, 2, new Object[]{"java/util/ArrayList", "java/lang/String"});
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
            methodVisitor.visitInsn(POP);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitLineNumber(53, label5);
            methodVisitor.visitInsn(RETURN);
            Label label6 = new Label();
            methodVisitor.visitLabel(label6);
            methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label6, 0);
            methodVisitor.visitLocalVariable("label", "Ljava/lang/String;", null, label0, label6, 1);
            methodVisitor.visitLocalVariable("value", "Ljava/lang/Object;", null, label0, label6, 2);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}

