# GTNH 模板代码参考

> 来源：ExampleMod1.7.10/src/main/java/com/myname/mymodid/

## 主模组类模板

### MyMod.java - 入口类

```java
package com.myname.mymodid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = MyMod.MODID, version = Tags.VERSION, name = "MyMod", acceptedMinecraftVersions = "[1.7.10]")
public class MyMod {

    public static final String MODID = "mymodid";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
```

**关键点**：
- `@Mod` 注解：声明模组基本信息
- `@SidedProxy`：客户端/服务端代理分离
- `Tags.VERSION`：自动版本替换（需配置 `generateGradleTokenClass`）
- `@Mod.EventHandler`：生命周期方法

---

### CommonProxy.java - 通用代理

```java
package com.myname.mymodid;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MyMod.LOG.info(Config.greeting);
        MyMod.LOG.info("I am MyMod at version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
```

**关键点**：
- `preInit`：加载配置、注册方块/物品
- `init`：注册配方、数据结构
- `postInit`：处理与其他模组的交互

---

### ClientProxy.java - 客户端代理

```java
package com.myname.mymodid;

public class ClientProxy extends CommonProxy {
    // 覆盖 CommonProxy 方法以实现客户端特定行为
    // 例如：注册渲染器、客户端事件
}
```

---

### Config.java - 配置类

```java
package com.myname.mymodid;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String greeting = "Hello World";

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
```

**关键点**：
- 使用 Forge `Configuration` API
- `CATEGORY_GENERAL`：通用配置分类
- 检查 `hasChanged()` 并保存

---

## mcmod.info 模板

```json
{
  "modid": "${modId}",
  "name": "${modName}",
  "description": "Example mod template.",
  "version": "${modVersion}",
  "mcversion": "${minecraftVersion}",
  "url": "http://www.example.com/",
  "updateUrl": "",
  "authorList": ["Author1"],
  "credits": "",
  "logoFile": "",
  "screenshots": [],
  "dependencies": []
}
```

---

## 本项目参考

AE2-QoL-1.7.10-GTNH 项目的入口类结构：

```
src/main/java/com/example/ae2qol/
├── AE2QoL.java          # 主入口类
├── CommonProxy.java      # 通用代理
├── ClientProxy.java      # 客户端代理
└── Config.java           # 配置类
```

Mixin 配置参考：
- Mixin JSON: `src/main/resources/mixins.ae2qol.json`
- Mixin 包路径: `com.example.ae2qol.mixin`
