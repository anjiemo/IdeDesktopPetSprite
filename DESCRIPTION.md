<p><b>IDE Desktop Pet Sprite — A desktop floating pet that reacts to your IDE's state.</b></p>
<p>
    Show a always-on-top desktop companion while you work in IntelliJ IDEA or Android Studio.
    Gradle sync, indexing, build/compile, and run/debug each trigger a distinct animation and status badge;
    success and failure bring celebration or dejected expressions.
    Replaceable sprites — built-in original slime <b>Gel</b> by default.
</p>
<br/>
<p>
    <b>
        <a href="https://github.com/anjiemo/IdeDesktopPetSprite">GitHub</a> |
        <a href="https://github.com/anjiemo/IdeDesktopPetSprite/blob/master/docs/README.zh-CN.md">中文文档</a> |
        <a href="https://github.com/anjiemo/IdeDesktopPetSprite#build--install">Get Started</a> |
        <a href="https://github.com/anjiemo/IdeDesktopPetSprite/blob/master/PRIVACY.md">Privacy</a>
    </b>
</p>
<br/>
<h2>Features</h2>
<ul>
    <li>State-aware animations: Gradle sync, indexing, build/compile, run/debug, success, failure, and idle.</li>
    <li>Always-on-top floating window — draggable, resizable (S / M / L), position remembered (multi-monitor safe).</li>
    <li>Switch character: built-in Gel, local sprite upload (PNG / WebP / GIF), or online <a href="https://petdex.dev">Petdex</a> catalog.</li>
    <li>Per-project character override with preview in settings.</li>
    <li>Right-click menu: reset position, resize, switch character, temporarily hide, or permanently disable.</li>
    <li>Transparent background; never steals editor focus.</li>
    <li>Settings → Tools → <b>Desktop Pet Sprite</b>, or <b>Tools → Desktop Pet Sprite Settings</b>, or Search Everywhere for <b>Desktop Pet</b> / <b>桌宠</b>.</li>
</ul>
<br/>
<h2>State mapping</h2>
<table>
    <tr><th>IDE event</th><th>Pet state</th></tr>
    <tr><td>Gradle sync</td><td>Thinking · Sync</td></tr>
    <tr><td>Indexing</td><td>Looking around · Index</td></tr>
    <tr><td>Build / compile</td><td>Running · Build</td></tr>
    <tr><td>Run / debug</td><td>Running (faster) · Run</td></tr>
    <tr><td>Success</td><td>Jumping · Done ✓</td></tr>
    <tr><td>Failure</td><td>Dejected · Error ✗</td></tr>
    <tr><td>Idle</td><td>Idle</td></tr>
</table>
<p>When multiple activities overlap, priority is: <b>run &gt; build &gt; sync &gt; index &gt; idle</b>.</p>
<br/>
<h2>Compatibility</h2>
<p>Requires IntelliJ Platform build <b>242+</b> (2024.2 or later). Works with IntelliJ IDEA, Android Studio, and other IntelliJ-based IDEs.</p>
<br/>
<hr/>
<br/>
<h2>IDE Desktop Pet Sprite · 构建状态桌宠</h2>
<p>
    在 Gradle Sync、建立索引、构建/编译、运行/调试 等不同状态下，
    于桌面显示一只会随状态变化的悬浮宠物，并在成功 / 失败时给出表情反馈。
    宠物素材可替换（默认内置原创史莱姆 Gel）。
</p>
<br/>
<h2>功能</h2>
<ul>
    <li>同步 / 索引 / 构建 / 运行 / 成功 / 失败 / 待机 —— 各有不同动画与状态徽标</li>
    <li>悬浮窗置顶、可拖拽、可调大小（小/中/大），位置自动记忆（多屏安全）</li>
    <li>可切换形象：内置 Gel、本地上传精灵图（PNG / WebP / GIF）、在线形象库 <a href="https://petdex.dev">Petdex</a>；支持按项目设置不同形象</li>
    <li>右键菜单：重置位置 / 调整尺寸 / 切换形象 / 临时隐藏 / 永久关闭</li>
    <li>透明背景，不抢占编辑器焦点</li>
    <li>Settings → Tools → Desktop Pet Sprite（桌面宠物），或 Tools → Desktop Pet Sprite 设置…，或双击 Shift 搜索「桌宠」</li>
</ul>
<br/>
<h2>兼容版本</h2>
<p>需要 IntelliJ Platform build <b>242+</b>（2024.2 及以上）。适用于 IntelliJ IDEA、Android Studio 及其他基于 IntelliJ 的 IDE。</p>
<br/>
<p>
    作者 <a href="https://github.com/anjiemo/">anjiemo</a> ·
    Copyright © 2026 anjiemo ·
    <a href="https://github.com/anjiemo/IdeDesktopPetSprite/blob/master/LICENSE">Apache License 2.0</a>
</p>
<br/>
<h2>Privacy · 隐私</h2>
<p>
    No personal data is collected.
    Optional Petdex downloads contact <a href="https://petdex.dev">petdex.dev</a> over HTTPS only when you use that feature.
    See the <a href="https://github.com/anjiemo/IdeDesktopPetSprite/blob/master/PRIVACY.md">Privacy Policy</a>.
</p>
<p>
    不收集个人数据；仅在使用 Petdex 在线形象库时经 HTTPS 访问 petdex.dev。
    详见 <a href="https://github.com/anjiemo/IdeDesktopPetSprite/blob/master/docs/PRIVACY.zh-CN.md">隐私政策</a>。
</p>
