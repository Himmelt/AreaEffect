# 点亮区域

### 简介

该Mod可以在服务端创建特殊亮度 (黑暗)的区域，当玩家进入该区域时，其客户端会切换对应亮度（Gamma值），

玩家离开区域时恢复原亮度。 通过提高Gamma值，可以不用添加发光方块而达到提高建筑内部亮度的效果，同时能完美地消除建筑内部不美观的阴影。
如果将Gamma值设置得较低（小于1）还可以实现将指定区域的夜色变得更黑暗的效果。
**注意，Gamma值带来的亮度只是画面效果，并不会改变实际亮度，也不会改变阳光传感器检测到的值，不会影响刷怪。**

### 特性

1. 手持选区工具 (默认木斧)左键选择起点，右键选择终点。

2. 每个区域设置独立的亮度和过渡速度。

3. 在安装有WE-CUI的客户端，可以像WE一样显示选区边界。

4. 单文件支持 1.7.10-1.12.2 版本，1.13+ 使用其他文件。

5. 配置文件已转移到主世界存档目录下，单机时每个存档都使用各自的配置文件，服务器则使用主世界存档目录下的配置文件。

### 指令

**所有指令只能由OP权限等级2+的玩家执行**

```

/areaeffect pos1                    设置玩家当前位置为选区起点

/areaeffect pos2                    设置玩家当前位置为选区终点

/areaeffect create [lightness] [duration]  根据选区创建区域，可选参数[light:小数]为目标亮度 (CIE L*, 0-100),[speed:小数]为过渡时长(秒)

/areaeffect delete                  删除玩家当前所在的区域

/areaeffect info                    显示玩家当前所在区域的信息，如果客户端安装有WE-CUI，则会显示范围

/areaeffect list [dim|all]          列出(某世界的)所有区域，没有参数时为玩家所在世界，参数为all时为全部世界，参数为整数时，为对应维度的世界

/areaeffect tp <id>                 传送到指定<id:整数>的区域的中心位置(无视方块阻碍)

/areaeffect lightness [value]           查看/设置当前区域的亮度 [value:小数]

/areaeffect duration [value]           查看/设置当前区域的过渡时长(秒) [value:小数]（变化值/tick）

/areaeffect tool                    手持为空，查看选区工具；手持非空，设置选区工具为当前手持物

```

### 更新日志

```yaml

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

This mod can create areas with specific light.

When players move into these areas, their clients will change the light (gamma) to the specific light.

When they move out the area, the client will restore the original light. By increase the gamma value, we can light the
buildings' inside without luminous block

and erase all the dark corners of the buildings. If we set the gamma lower than 1.0, we can make the area darker.
**Notice, The light of gamma is just video effect, the real light (value of Daylight Sensor) is not changed.**

### Features

1. Hold the select tool (default WoodenAxe), left click to set the start point,

right click to set the end point.

2. The client with WE-CUI mod can show the selected area's border.

3. Single file supports 1.7.10 version and higher.

4. Config file has been moved to world's save location, each save has a config file. On server side, config is loaded
   from overworld's save location.

### Commands

```

/areaeffect pos1             set player's pos as area start point

/areaeffect pos2             set player's pos as area end point

/areaeffect create [lightness]   create area by current selected area, the optional arg is light (range: -15.0 - 15.0)

/areaeffect delete           delete the area at player's pos

/areaeffect info             show the info of the area at player's pos

/areaeffect list [dim|all]   list the world's areas, empty args using player's world, arg "all" will list all world's areas, 

int arg using the world with the dimension

/areaeffect tp <id>          teleport to area's center of the id

/areaeffect level [lightness]    get/set the current area's light

/areaeffect speed [duration]    get/set the current area's light change speed (float/tick)

/areaeffect tool             hand empty, show select tool; hand item,set select tool to the item in hand

```

