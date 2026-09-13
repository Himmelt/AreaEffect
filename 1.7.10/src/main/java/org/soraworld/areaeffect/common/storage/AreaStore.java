package org.soraworld.areaeffect.common.storage;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.config.Configuration;
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
 */
public class AreaStore {

    private static final Logger LOGGER = LogManager.getLogger("AreaEffect");

    private final Configuration config;

    /** 随世界存储的区域文件；未设置时只读写配置、不碰区域数据。 */
    private File file = null;

    private boolean dirty = false;

    public AreaStore(Configuration config) {
        this.config = config;
    }

    /** 设置随世界存储的区域文件（一个世界一个文件）。 */
    public void setFile(File file) {
        this.file = file;
    }

    /** 读取选区工具名配置项。 */
    public String readToolName() {
        return config.getString("tool", "general", "wooden_axe", "Select Tool");
    }

    /** 写入选区工具名配置项（只改内存配置，落盘由 {@link #flush(AreaTable)} 负责）。 */
    public void writeToolName(String name) {
        config.get("general", "tool", "wooden_axe", "Select Tool").set(name);
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

    /** 若有未落盘的改动则写盘（配置 + 区域 NBT）；无改动直接返回。 */
    public void flush(AreaTable table) {
        if (!dirty) {
            return;
        }
        dirty = false;
        config.save();
        if (file != null) {
            writeAreasNbt(table, file);
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
            NBTTagList areas = root.getTagList("areas", 10);
            for (int i = 0; i < areas.tagCount(); i++) {
                NBTTagCompound tag = areas.getCompoundTagAt(i);
                int dim = tag.getInteger("dim");
                Area area;
                if (tag.hasKey("shape")) {
                    AreaShape shape = ShapeTypes.fromNbt(tag.getCompoundTag("shape"));
                    if (shape == null) {
                        continue;
                    }
                    area = new Area(shape);
                } else {
                    // 旧存档兼容：无 shape 键按 box 从 x1..z2 读取
                    area = Area.box(tag.getInteger("x1"), tag.getInteger("y1"), tag.getInteger("z1"),
                            tag.getInteger("x2"), tag.getInteger("y2"), tag.getInteger("z2"));
                }
                area.setRemark(tag.getString("remark"));
                area.setEffects(readEffectsNbt(tag.getTagList("effects", 10)));
                area.id = tag.getInteger("id");
                table.put(dim, area.id, area);
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
        for (int i = 0; i < list.tagCount(); i++) {
            AreaEffect effect = EffectTypes.fromNbt(list.getCompoundTagAt(i));
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    /**
     * 把区域以 NBT 原子写入随世界文件：先写临时文件再替换，避免崩溃损坏。
     */
    private void writeAreasNbt(AreaTable table, File file) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList areas = new NBTTagList();
        table.byDim().forEach((dim, dimAreas) -> dimAreas.values().forEach(area -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dim);
            tag.setInteger("id", area.id);
            NBTTagCompound shapeTag = new NBTTagCompound();
            ShapeTypes.writeNbt(area.shape(), shapeTag);
            tag.setTag("shape", shapeTag);
            tag.setString("remark", area.getRemark());
            NBTTagList effectList = new NBTTagList();
            for (AreaEffect effect : area.getEffects()) {
                NBTTagCompound effectTag = new NBTTagCompound();
                effectTag.setString("type", effect.typeId());
                effect.writeToNbt(effectTag);
                effectList.appendTag(effectTag);
            }
            tag.setTag("effects", effectList);
            areas.appendTag(tag);
        }));
        root.setTag("areas", areas);
        File tmp = new File(file.getPath() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
                CompressedStreamTools.writeCompressed(root, out);
            }
            if (!tmp.renameTo(file)) {
                java.nio.file.Files.copy(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
        } catch (Throwable t) {
            LOGGER.warn("写入区域存档失败: {}", file, t);
        }
    }
}
