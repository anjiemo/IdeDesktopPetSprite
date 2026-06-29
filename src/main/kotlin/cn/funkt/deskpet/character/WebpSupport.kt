/*
 * Copyright 2026 anjiemo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.funkt.deskpet.character

import com.intellij.openapi.diagnostic.thisLogger
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi
import javax.imageio.spi.IIORegistry

/**
 * 让 [javax.imageio.ImageIO] 支持解码 WebP。
 *
 * IntelliJ 插件运行在独立的类加载器中，默认的 IIORegistry 不会扫描到插件 jar
 * 里 META-INF/services 声明的 ImageIO 插件，因此这里手动把 TwelveMonkeys 的
 * WebP 读取 SPI 注册进去。注册一次即可，全进程生效。
 */
object WebpSupport {

    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            runCatching {
                IIORegistry.getDefaultInstance().registerServiceProvider(WebPImageReaderSpi())
            }.onFailure {
                thisLogger().warn("注册 WebP 解码器失败，webp 形象将无法加载", it)
            }
            registered = true
        }
    }
}
