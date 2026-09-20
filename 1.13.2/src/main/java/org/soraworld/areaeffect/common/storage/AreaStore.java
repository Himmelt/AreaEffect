package org.soraworld.areaeffect.common.storage;

import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.shape.ShapeTypes;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 区域存档与模组配置的持久化。
 *
 * <p>职责边界：只管"怎么存"，不管"存什么"——数据本身由 {@link AreaTable} 持有，读写时传入。
 *
 * <p>两个要点：
 * <ul>
 *   <li><b>原子写入</b>：先写临时文件再替换，避免写盘途中崩溃损坏存档；</li>
 *   <li><b>合并落盘</b>：{@link #markDirty()} 只置脏标记，真正的写盘在 {@link #flush(AreaTable)}
 *       执行，由服务端每 tick 调用一次，因此 GUI 里连续调参不会每次都全量重写文件。</li>
 * </ul>
 *
 * <p>1.13+ 旧的 forge Configuration 类已移除，工具名等少量设置改用 Forge 自带的
 * NightConfig（{@link FileConfig}，TOML）；区域数据仍是随世界的独立 NBT 文件。
 */
public class AreaStore {

    private static final Logger LOGGER = LogManager.getLogger("AreaEffect");

    private static final String DEFAULT_TOOL = "wooden_axe";
    private static final String KEY_TOOL = "tool";

    private final FileConfig config;

    /** 随世界存储的区域文件；未设置时只读写配置、不碰区域数据。 */
    private File file = null;

    private boolean dirty = false;

    public AreaStore(File configFile) {
        this.config = FileConfig.of(configFile);
    }

    /** 设置随世界存储的区域文件（一个世界一个文件）。 */
    public void setFile(File file) {
        this.file = file;
    }

    /** 读取选区工具名配置项。 */
    public String readToolName() {
        Object value = config.get(KEY_TOOL);
        return value instanceof String ? (String) value : DEFAULT_TOOL;
    }

    /** 写入选区工具名配置项（只改内存配置，落盘由 {@link #flush(AreaTable)} 负责）。 */
    public void writeToolName(String name) {
        config.set(KEY_TOOL, name);
    }

    /** 载入配置，并把随世界的区域数据读入给定数据集（会先清空该数据集）。 */
    public void load(AreaTable table) {
        config.load();
        table.clear();
        dirty = false;
        if (file != null && file.exists()) {
            readAreasNbt(table, file);
        }
    }

    /** 标记存档为脏，等待下一次 {@link #flush(AreaTable)} 合并落盘。 */
    public void markDirty() {
        dirty = true;
    }

    /**
     * 若有未落盘的改动则写盘（配置 + 区域 NBT）；无改动直接返回。
     *
     * <p>脏标记只在<b>写盘成功之后</b>才清：写盘失败（磁盘满、文件被占用、权限不足等）时保留标记，
     * 由服务端每 tick 的 {@code flushStore} 与关服兜底继续重试 —— 否则这次改动会被静默丢掉，
     * 连关服那次兜底 flush 都会因为"不脏"而直接返回。
     */
    public void flush(AreaTable table) {
        if (!dirty) {
            return;
        }
        try {
            config.save();
            if (file != null && !writeAreasNbt(table, file)) {
                return; // 写失败：保留脏标记等下次重试
            }
        } catch (Throwable t) {
            LOGGER.warn("写入模组配置失败，保留脏标记待重试", t);
            return;
        }
        dirty = false;
    }

    /** 关服时释放配置文件资源。 */
    public void close() {
        try {
            config.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 从随世界的 NBT 文件读入区域。
     */
    private void readAreasNbt(AreaTable table, File file) {
        try {
            NBTTagCompound root;
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                root = CompressedStreamTools.readCompressed(in);
            }
            // 1.13+ NBTTagList 访问改名：getTagList→getList、tagCount→size、getCompoundTagAt→getCompound
            NBTTagList areas = root.getList("areas", 10);
            for (int i = 0; i < areas.size(); i++) {
                NBTTagCompound tag = areas.getCompound(i);
                int dim = tag.getInteger("dim");
                Area area;
                if (tag.contains("shape")) {
                    AreaShape shape = ShapeTypes.fromNbt(tag.getCompound("shape"));
                    if (shape == null) {
                        continue;
                    }
                    area = new Area(shape);
                } else {
                    // 旧存档兼容：无 shape 键按 box 从 x1..z2 读取。
                    // 缺坐标键时 getInteger 会返回 0，生成仅在 (0,0,0) 的单格幽灵区域 —— 检出即跳过并记录
                    if (!tag.contains("x1") || !tag.contains("y1") || !tag.contains("z1")
                            || !tag.contains("x2") || !tag.contains("y2") || !tag.contains("z2")) {
                        LOGGER.warn("存档条目缺少形状或坐标键（dim={} id={}），已跳过以免产生 (0,0,0) 单格幽灵区域",
                                dim, tag.contains("id") ? tag.getInteger("id") : -1);
                        continue;
                    }
                    area = Area.box(tag.getInteger("x1"), tag.getInteger("y1"), tag.getInteger("z1"),
                            tag.getInteger("x2"), tag.getInteger("y2"), tag.getInteger("z2"));
                }
                area.setRemark(tag.getString("remark"));
                area.setEffects(readEffectsNbt(tag.getList("effects", 10)));
                area.id = tag.getInteger("id");
                Area prev = table.put(dim, area.id, area);
                if (prev != null) {
                    LOGGER.warn("存档中出现重复区域 id {} @维度 {}，先前条目将被覆盖", area.id, dim);
                }
                table.observeId(area.id);
            }
        } catch (Throwable t) {
            LOGGER.warn("读取区域存档失败: {}", file, t);
        }
    }

    /**
     * 从 NBT 效果列表反序列化出效果集合。
     */
    private List<AreaEffect> readEffectsNbt(NBTTagList list) {
        List<AreaEffect> effects = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            AreaEffect effect = EffectTypes.fromNbt(list.getCompound(i));
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    /**
     * 把区域以 NBT 原子写入随世界文件：先写临时文件再替换，避免崩溃损坏。
     *
     * @return 是否写入成功；失败时由 {@link #flush(AreaTable)} 保留脏标记以便重试
     */
    private boolean writeAreasNbt(AreaTable table, File file) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList areas = new NBTTagList();
        table.byDim().forEach((dim, dimAreas) -> dimAreas.values().forEach(area -> {
            NBTTagCompound tag = new NBTTagCompound();
            // 1.13+ NBT 写入统一为 putXxx / add
            tag.putInt("dim", dim);
            tag.putInt("id", area.id);
            NBTTagCompound shapeTag = new NBTTagCompound();
            ShapeTypes.writeNbt(area.shape(), shapeTag);
            tag.put("shape", shapeTag);
            tag.putString("remark", area.getRemark());
            NBTTagList effectList = new NBTTagList();
            for (AreaEffect effect : area.getEffects()) {
                NBTTagCompound effectTag = new NBTTagCompound();
                effectTag.putString("type", effect.typeId());
                effect.writeToNbt(effectTag);
                effectList.add(effectTag);
            }
            tag.put("effects", effectList);
            areas.add(tag);
        }));
        root.put("areas", areas);
        File tmp = new File(file.getPath() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
                CompressedStreamTools.writeCompressed(root, out);
            }
            if (!tmp.renameTo(file)) {
                java.nio.file.Files.copy(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("写入区域存档失败: {}", file, t);
            return false;
        }
    }
}
