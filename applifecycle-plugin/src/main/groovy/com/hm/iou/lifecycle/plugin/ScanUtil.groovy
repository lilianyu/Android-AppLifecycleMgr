package com.hm.iou.lifecycle.plugin

import java.util.jar.JarEntry
import java.util.jar.JarFile

class ScanUtil {

    static File FILE_CONTAINS_INIT_CLASS

    /**
     * 判断该class是否是我们的目标类
     *
     * @param file
     * @return
     */
    static boolean isTargetProxyClass(File file) {
        if (file.name.endsWith(GlobalConfig.PROXY_CLASS_SUFFIX) && file.name.startsWith(GlobalConfig.PROXY_CLASS_PREFIX)) {
            return true
        }
        return false
    }

    /**
     * 扫描jar包里的所有class文件：
     * 1.通过包名识别所有需要注入的类名
     * 2.找到注入管理类所在的jar包，后面我们会在该jar包里进行代码注入
     *
     * @param jarFile
     * @param destFile
     * @return
     */
    static List<String> scanJar(File jarFile, File destFile) {
        def file = new JarFile(jarFile)
        Enumeration<JarEntry> enumeration = file.entries()
        List<String> list = null
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement()
            String entryName = jarEntry.getName()
            if (entryName == GlobalConfig.REGISTER_CLASS_FILE_NAME) {
                //标记这个jar包包含 ApplicationLifecycleManager.class
                //扫描结束后，我们会生成注册代码到这个文件里
                FILE_CONTAINS_INIT_CLASS = destFile
            } else {
                if (entryName.startsWith(GlobalConfig.PROXY_CLASS_PACKAGE_NAME)) {
                    if (list == null) {
                        list = new ArrayList<>()
                    }

                    println "entryName = ${entryName}"
                    def entry = entryName.substring(entryName.lastIndexOf("/") + 1)
                    if (entry && !entry.allWhitespace) {
                        list.add(entry)
                    }
                }
            }
        }
        return list
    }

    static boolean shouldProcessPreDexJar(String path) {
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }

}