# 点亮区域

### 简介

该Mod可以在服务端创建指定感知亮度的区域。玩家进入区域时，客户端会把光照贴图平滑混合到该区域的
目标亮度（CIE L*，0-100）；离开区域时再平滑恢复原画面。

由于实现于光照贴图的接收端，玩家自身的 Gamma 设置照常生效且不会被读写。提高目标亮度可以不用添加
发光方块就提升建筑内部亮度，并消除不美观的阴影；设置得低于环境亮度时还能让指定区域更暗。
**注意，亮度只是画面效果，并不会改变实际亮度，也不会改变阳光传感器检测到的值，不会影响刷怪。**

### 特性

1. 手持选区工具 (默认木斧) 左键选点、右键选点。支持 7 种选区形状：长方体、全高方柱、圆柱、
   全高圆柱、球体、多边形柱、全高多边形柱；手持工具时 Shift+右键空气可轮切形状，屏幕中央显示当前形状。

   多边形形状（多边形柱 / 全高多边形柱）的选点方式不同：左键逐个追加顶点，右键空气撤回上一个顶点；
   顶点达到 3 个即自动闭合、可以创建，不需要手动闭合操作。单个选区最多 64 个顶点。

2. 每个区域独立设置目标亮度（CIE L*，0-100）与过渡时长（0-60 秒，0 表示不过渡、瞬间变化），进出区域时画面平滑过渡。

3. 选区与区域线框由本模组自绘，无需安装 WE-CUI 等外部模组。

4. 客户端按 J 打开区域管理面板（区域列表 / 传送 / 删除 / 修改亮度与时长 / 备注 / 线框开关），
   按 K 开关选区线框显示。

5. 区域数据随存档保存（`<存档目录>/areaeffect.dat`，一个世界一个文件）；模组配置文件仍在
   `config/areaeffect.cfg`。

6. 本分支（1.7.10）只支持 1.7.10，其它 MC 版本由对应分支/文件提供。

### 指令

**所有指令只能由OP权限等级2+的玩家执行；在服务端创建/修改区域同样要求该权限——
非OP玩家手持选区工具也无法选点。**

```

/areaeffect pos1                            设置玩家当前位置为选区起点

/areaeffect pos2                            设置玩家当前位置为选区终点

/areaeffect create [lightness] [duration]   根据当前选区创建区域；lightness 为目标亮度
                                            (CIE L*, 0-100，默认 100)，duration 为过渡时长(秒，默认 1)

/areaeffect tool                            手持为空时查看选区工具；手持非空时把选区工具设为当前手持物

```

**区域管理（列表 / 传送 / 删除 / 修改亮度与时长 / 备注 / 线框开关）已迁移到客户端面板，
按 J 打开，不再提供对应指令。** 详见上文「特性」。

### 更新日志

```yaml

1.4.0:

  - 选区形状扩展为 7 种（长方体 / 全高方柱 / 圆柱 / 全高圆柱 / 球体 / 多边形柱 / 全高多边形柱），
    手持工具时 Shift+右键空气轮切，屏幕中央显示当前形状

  - 新增区域管理面板（默认 J 键）：区域列表 / 传送 / 删除 / 亮度与时长（拖动实时预览） / 备注 / 线框开关

  - 新增区域备注（最长 60 字符）；创建时若与已有区域冲突，会自动显示冲突区域的线框

  - 区域数据随存档保存（存档目录下的 areaeffect.dat，一个世界一个文件，临时文件+替换的原子写入）

  - 亮度语义统一为 CIE L* (0-100)，过渡时长以秒计；区域管理相关指令不再提供，改由面板操作

1.3.0:

  - 亮度实现改为接管光照贴图接收端：不再读写玩家的 Gamma 设置，而是在光照贴图上传前叠加统一的 CIE L* 亮度偏移

  - 区域内画面实时跟随玩家自己的 Gamma 设置变化；离开区域后精确恢复 vanilla 画面（偏移归零）

  - 数值语义不变：区域亮度仍为 CIE L* (0-100)，过渡时长仍以秒计

1.2.0:

  - 调整代码结构

  - 增加每区域独立设置过渡时长(秒)

  - 增加 1.13+ 版本支持

1.1.0:

  - 修正翻译

1.0.8:

  - 添加 list 指令，列出区域列表，并可以通过点击文字传送

  - 添加 tp 指令，可以传送到指定id区域的中心

  - 修复 原始亮度，原始亮度最大值限制为1.0

1.0.6:

  - 添加 speed 指令，添加亮度过渡时长(秒)配置项和对应功能

  - 调整 配置文件存放位置调整为存档目录

1.0.5:

  - 添加 1.8|1.8.8|1.8.9|1.9|1.9.4 支持

  - 修复 1.8 事件BUG

  - 添加 区域冲突提示，不允许区域之间有重叠部分

1.0.3:

  - 更新 对 1.7.10|1.10.x|1.11.x|1.12.x 支持

1.0.1:

  - 调整 亮度范围调整为 [-15.0 - 15.0]

1.0.0:

  - 添加 WE_CUI 支持

```

# AreaEffect

### Description

This mod creates areas with a specific perceived lightness.

When players enter such an area, their client smoothly blends the rendered lightmap to the area's
target brightness (CIE L*, 0-100); when they leave, the client blends back to the vanilla screen.

Because it works on the receiving end of the lightmap, the player's own gamma setting keeps working
and is never modified. Raising the target lightness brightens a building's interior without adding
luminous blocks and removes unsightly dark corners; setting it below the ambient level makes the
area darker.
**Notice: the lightness is a screen effect only. The real light level (including what a Daylight Sensor reads) is not changed, and mob spawning is not affected.**

### Features

1. Hold the select tool (default WoodenAxe): left click to set one point, right click to set another.
   7 shape types are supported: box, full square pillar, cylinder, full round pillar, sphere,
   polygon prism and full polygon pillar. While holding the tool, Shift + right click in air cycles
   the shape, and an overlay shows the current one.

   The polygon shapes (polygon prism / full polygon pillar) are picked differently: left click
   appends a vertex, right click in air undoes the last one; once 3 vertices are reached the polygon
   closes automatically and can be created — there is no manual closing step. A single selection
   holds at most 64 vertices.

2. Each area has its own target lightness (CIE L*, 0-100) and transition duration (0-60 s, where 0
   means no transition — an instant change); the screen fades smoothly when entering or leaving an area.

3. Selection and area wireframes are drawn by this mod itself — no WE-CUI or other mod required.

4. Press J to open the area manager (area list / teleport / delete / edit lightness and duration /
   remark / wireframe toggle); press K to toggle the selection wireframe.

5. Area data is stored per world at `<save dir>/areaeffect.dat`; the mod config file stays at
   `config/areaeffect.cfg`.

6. This branch supports 1.7.10 only; other MC versions are provided by their own branches/files.

### Commands

Only players with OP permission level 2+ can use these; the same permission is required to create
or modify areas on the server — without it even holding the select tool lets you pick nothing.

```

/areaeffect pos1                             set player's pos as the selection start point

/areaeffect pos2                             set player's pos as the selection end point

/areaeffect create [lightness] [duration]    create an area from the current selection; lightness is
                                             CIE L* (0-100, default 100), duration in seconds (default 1)

/areaeffect tool                             hand empty: show the select tool; holding an item: set it as the select tool

```

**Area management (list / teleport / delete / edit lightness and duration / remark / wireframe)
has moved to the client-side panel — press J. No commands are provided for it any more.**

