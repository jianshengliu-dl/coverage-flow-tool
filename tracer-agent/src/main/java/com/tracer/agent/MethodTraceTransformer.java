package com.tracer.agent;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Bytecode transformer using ASM AdviceAdapter.
 * Injects TraceRecorder.enter() / TraceRecorder.exit() at every method entry and exit.
 */
public class MethodTraceTransformer implements ClassFileTransformer {

    private final String basePackage; // e.g. "com/psa"

    public MethodTraceTransformer(String basePackage) {
        this.basePackage = basePackage;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain domain, byte[] classfileBuffer) {
        if (className == null) return null;
        if (!basePackage.isEmpty() && !className.startsWith(basePackage)) return null;
        // Skip agent classes themselves
        if (className.startsWith("com/tracer/agent")) return null;
        // Skip lambda / synthetic
        if (className.contains("$$") || className.contains("$Lambda")) return null;

        try {
            ClassReader  cr = new ClassReader(classfileBuffer);
            ClassWriter  cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new TraceClassVisitor(cw, className);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Throwable t) {
            // Never crash the target app
            return null;
        }
    }

    // ----------------------------------------------------------------
    private static class TraceClassVisitor extends ClassVisitor {
        private final String className;

        TraceClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // Skip synthetic, bridge, abstract, native
            if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE
                         | Opcodes.ACC_ABSTRACT  | Opcodes.ACC_NATIVE)) != 0) return mv;
            // Skip constructors and static initializers
            if (name.equals("<init>") || name.equals("<clinit>")) return mv;
            return new TraceMethodAdapter(mv, access, name, descriptor, className);
        }
    }

    // ----------------------------------------------------------------
    private static class TraceMethodAdapter extends AdviceAdapter {
        private final String className;
        private final String methodName;

        TraceMethodAdapter(MethodVisitor mv, int access, String name,
                           String descriptor, String className) {
            super(Opcodes.ASM9, mv, access, name, descriptor);
            this.className  = className.replace('/', '.');
            this.methodName = name;
        }

        @Override
        protected void onMethodEnter() {
            // TraceRecorder.enter(className, methodName)
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/tracer/agent/TraceRecorder", "enter",
                "(Ljava/lang/String;Ljava/lang/String;)V", false);
        }

        @Override
        protected void onMethodExit(int opcode) {
            // TraceRecorder.exit(className, methodName)
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/tracer/agent/TraceRecorder", "exit",
                "(Ljava/lang/String;Ljava/lang/String;)V", false);
        }
    }
}
