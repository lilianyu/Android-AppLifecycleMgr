package com.hm.iou.lifecycle.plugin

import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class AppLifecycleCodeInjector {

    List<String> proxyAppLifecycleClassList

    AppLifecycleCodeInjector(List<String> list) {
        proxyAppLifecycleClassList = list
    }

    void execute() {
        println("开始执行ASM方法======>>>>>>>>")

        File srcFile = ScanUtil.FILE_CONTAINS_INIT_CLASS
        //创建一个临时jar文件，要修改注入的字节码会先写入该文件里
        def optJar = new File(srcFile.getParent(), srcFile.name + ".opt")
        if (optJar.exists())
            optJar.delete()
        def file = new JarFile(srcFile)
        Enumeration<JarEntry> enumeration = file.entries()
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar))
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement()
            String entryName = jarEntry.getName()
            ZipEntry zipEntry = new ZipEntry(entryName)
            InputStream inputStream = file.getInputStream(jarEntry)
            jarOutputStream.putNextEntry(zipEntry)

            //找到需要插入代码的class，通过ASM动态注入字节码
            if (GlobalConfig.REGISTER_CLASS_FILE_NAME == entryName) {
                println "insert register code to class >> " + entryName

                ClassReader classReader = new ClassReader(inputStream)
                // 构建一个ClassWriter对象，并设置让系统自动计算栈和本地变量大小
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
                ClassVisitor classVisitor = new AppLifecycleClassVisitor(classWriter)
                //开始扫描class文件
                classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

                byte[] bytes = classWriter.toByteArray()
                //将注入过字节码的class，写入临时jar文件里
                jarOutputStream.write(bytes)
            } else {
                //不需要修改的class，原样写入临时jar文件里
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            inputStream.close()
            jarOutputStream.closeEntry()
        }

        jarOutputStream.close()
        file.close()

        //删除原来的jar文件
        if (srcFile.exists()) {
            srcFile.delete()
        }
        //重新命名临时jar文件，新的jar包里已经包含了我们注入的字节码了
        optJar.renameTo(srcFile)
        println "AppLifecycleCodeInjector srcFile=${srcFile.getAbsolutePath()}"
    }

    class AppLifecycleClassVisitor extends ClassVisitor {

        private ClassVisitor mClassVisitor

        AppLifecycleClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor)
            mClassVisitor = classVisitor
        }

        @Override
        MethodVisitor visitMethod(int access, String name,
                                  String desc, String signature,
                                  String[] exception) {
            println "visit method: " + name
            MethodVisitor methodVisitor = mClassVisitor.visitMethod(access, name, desc, signature, exception)
            //找到 AppLifeCycleManager里的init()方法
            if ("init" == name) {
                methodVisitor = new LoadAppLifecycleMethodAdapter(methodVisitor, access, name, desc)
            }
            return methodVisitor
        }
    }

    class LoadAppLifecycleMethodAdapter extends AdviceAdapter {

        LoadAppLifecycleMethodAdapter(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv, access, name, desc)
        }

        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            println "-------onMethodEnter------"
            proxyAppLifecycleClassList.forEach({ proxyClassName ->
                println "开始注入代码：${proxyClassName}"
                def fullName = GlobalConfig.PROXY_CLASS_PACKAGE_NAME.replace("/", ".") + "." + proxyClassName.substring(0, proxyClassName.length() - 6)
                println "full classname = ${fullName}"
                mv.visitLdcInsn(fullName)
                mv.visitMethodInsn(INVOKESTATIC, GlobalConfig.INJECT_CLASS_NAME, GlobalConfig.INJECT_METHOD_NAME, GlobalConfig.INJECT_PARAMS_DESC, false)
            })
        }

        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode)
            println "-------onMethodExit------"
        }
    }

}