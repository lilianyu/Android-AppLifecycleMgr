# 兼容 AGP 7.4.2版本
===================================

AppLifecycle插件是使用了 APT技术、gradle插件技术+ASM动态生成字节码，在编译阶段就已经完成了大部分工作，无性能问题、且使用方便。

# 1. **common组件依赖 applifecycle-api** 
首先，common组件通过 api 添加 applifecycle-api 依赖 并发布ARR：

```java
//common组件 build.gradle
dependencies {
    ...
    //AppLifecycle
    api 'com.github.hufeiyang.Android-AppLifecycleMgr:applifecycle-api:1.0.4'
}
```
# 2. **业务组件依赖applifecycle-compiler、实现接口+注解**
各业务组件都要 依赖最新common组件，并添加 applifecycle-compiler 的依赖：

```java
//业务组件 build.gradle
...
	//这里Common:1.0.2内依赖了applifecycle-api
    implementation 'com.github.hufeiyang:Common:1.0.2'
    annotationProcessor 'com.github.hufeiyang.Android-AppLifecycleMgr:applifecycle-compiler:1.0.4'
```
sync后，新建类来实现接口**IApplicationLifecycleCallbacks**用于接收Application生命周期，且添加@**AppLifecycle**注解。  

例如 Cart组件的实现：

```java
/**
 * 组件的AppLifecycle
 * 1、@AppLifecycle
 * 2、实现IApplicationLifecycleCallbacks
 * @author hufeiyang
 */
@AppLifecycle
public class CartApplication implements IApplicationLifecycleCallbacks {

    public  Context context;

    /**
      * 用于设置优先级，即多个组件onCreate方法调用的优先顺序
      * @return
     */
    @Override
    public int getPriority() {
        return NORM_PRIORITY;
    }

    @Override
    public void onCreate(Context context) {
        //可在此处做初始化任务，相当于Application的onCreate方法
        this.context = context;

        Log.i("CartApplication", "onCreate");
    }

    @Override
    public void onTerminate() {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onTrimMemory(int level) {
    }
}
```
实现的方法 有onCreate、onTerminate、onLowMemory、onTrimMemory。最重要的就是**onCreate方法了，相当于Application的onCreate方法，可在此处做初始化任务**。 
并且还可以通过getPriority()方法设置回调 多个组件onCreate方法调用的优先顺序，无特殊要求设置NORM_PRIORITY即可。

# 3. **壳工程引入AppLifecycle插件、触发回调**

壳工程引入新的common组件、业务组件，以及 引入AppLifecycle插件：

```java
//壳工程根目录的 build.gradle

buildscript {
   
    repositories {
        google()
        jcenter()

        //applifecycle插件仓也是jitpack
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.6.1'

        //加载插件applifecycle
        classpath 'com.github.hufeiyang.Android-AppLifecycleMgr:applifecycle-plugin:1.0.3'
    }
}
```

```java
//app module 的build.gradle

apply plugin: 'com.android.application'
//使用插件applifecycle
apply plugin: 'com.hm.plugin.lifecycle'
...
dependencies {
    ...
    //这里没有使用私有maven仓，而是发到JitPack仓，一样的意思~
//    implementation 'com.hfy.cart:cart:1.0.0'
    implementation 'com.github.hufeiyang.Cart:module_cart:1.0.11'
    implementation 'com.github.hufeiyang:HomePage:1.0.5'

    //壳工程内 也需要依赖Common组件，因为要 触发生命周期分发
    implementation 'com.github.hufeiyang:Common:1.0.2'
}
```
最后需要在Application中触发生命周期的分发：

```java
//壳工程 MyApplication
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ...

        ApplicationLifecycleManager.init();
        ApplicationLifecycleManager.onCreate(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        ApplicationLifecycleManager.onTerminate();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        ApplicationLifecycleManager.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        ApplicationLifecycleManager.onTrimMemory(level);
    }
}
```
首先在inCreate方法中调用 ApplicationLifecycleManager的init()方法，用于收集组件内实现了IApplicationLifecycleCallbacks且添加了@AppLifecycle注解的类。然后在各生命周期方法内调用对应的ApplicationLifecycleManager的方法，来分发到所有组件。

这样 组件 就能接收到Application的生命周期了。 **新增组件的话，只需要 实现IApplicationLifecycleCallbacks并添加了@AppLifecycle注解 即可，无需修改壳工程，也不用关心**。
