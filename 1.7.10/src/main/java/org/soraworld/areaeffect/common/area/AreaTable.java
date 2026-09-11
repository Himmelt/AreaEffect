package org.soraworld.areaeffect.common.area;

import net.minecraft.entity.player.EntityPlayer;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.util.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域数据集：维度 → 区域 id → {@link Area}，并提供与之相关的全部查询与变更。
 *
 * <p>服务端持有权威数据，客户端持有同一结构作为只读镜像；单机下两者其实是同一个实例，
 * 这也是"单机不需要回发同步包"的原因。
 *
 * <p>并发：容器为 {@link ConcurrentHashMap}，{@link Area} 内部用 volatile + copy-on-write
 * 保证渲染线程遍历安全，因此本类不做额外加锁——读取方拿到的永远是完整快照。
 */
public class AreaTable {

    private final Map<Integer, Map<Integer, Area>> byDim = new ConcurrentHashMap<>();

    /** 已分配的最大区域 id。单调递增，不因删除而回退，避免 id 复用。 */
    private int nextId = 0;

    /** 全部维度的区域表（只读遍历与持久化用）。 */
    public Map<Integer, Map<Integer, Area>> byDim() {
        return byDim;
    }

    /**
     * 指定维度的区域表；该维度尚无区域时返回空表。
     *
     * <p>注意返回空表而非 null 是为了让只读调用点省掉判空；但<b>不要</b>往返回值里写
     * （空表是不可变实例），需要写入请用 {@link #ensureDim(int)}。
     */
    public Map<Integer, Area> inDim(int dim) {
        Map<Integer, Area> dimAreas = byDim.get(dim);
        return dimAreas == null ? Collections.<Integer, Area>emptyMap() : dimAreas;
    }

    /**
     * 该维度是否已建立区域表。
     *
     * <p>注意语义是<b>"表是否存在"</b>而非"表里有没有区域"：某个维度的区域被全部删除后，
     * 空表仍会保留在容器里。{@code AreaRequests#onSetProps} 依赖这个区别来区分
     * "该维度还没有任何区域"（静默丢弃）与"区域存在但 id 不匹配"（提示 notfound）——
     * 这正是重构前 {@code lightAreas.get(dim) == null} 的原语义，不要改成判空。
     */
    public boolean hasDim(int dim) {
        return byDim.containsKey(dim);
    }

    /** 指定维度的区域表，不存在则创建（仅写入路径使用）。 */
    public Map<Integer, Area> ensureDim(int dim) {
        return byDim.computeIfAbsent(dim, d -> new ConcurrentHashMap<Integer, Area>());
    }

    public void put(int dim, int id, Area area) {
        ensureDim(dim).put(id, area);
    }

    public Area get(int dim, int id) {
        return inDim(dim).get(id);
    }

    /** 删除并返回被删区域；该维度或该 id 不存在时返回 null。 */
    public Area remove(int dim, int id) {
        Map<Integer, Area> dimAreas = byDim.get(dim);
        return dimAreas == null ? null : dimAreas.remove(id);
    }

    /** 清空全部数据并重置 id 分配器（重新载入存档时调用）。 */
    public void clear() {
        byDim.clear();
        nextId = 0;
    }

    /** 全部存在区域的维度（升序）。 */
    public List<Integer> dims() {
        List<Integer> result = new ArrayList<>(byDim.keySet());
        Collections.sort(result);
        return result;
    }

    /** 指定维度的区域快照，按 id 升序。 */
    public List<Area> sortedIn(int dim) {
        List<Area> result = new ArrayList<>(inDim(dim).values());
        Collections.sort(result, (a, b) -> Integer.compare(a.id, b.id));
        return result;
    }

    /** 分配一个新的全局区域 id。 */
    public int allocateId() {
        return ++nextId;
    }

    /** 载入存档时同步已用 id，保证此后分配的 id 不与存档中的重复。 */
    public void observeId(int id) {
        if (id > nextId) {
            nextId = id;
        }
    }

    /**
     * 玩家当前所在的区域。判定用玩家脚部所在方块（见 {@link Vec3d#Vec3d(net.minecraft.entity.Entity)}）；
     * 不在任何区域内返回 null。
     */
    public Area findAt(EntityPlayer player) {
        Vec3d pos = new Vec3d(player);
        for (Map.Entry<Integer, Area> entry : inDim(player.dimension).entrySet()) {
            Area area = entry.getValue();
            if (area.contains(pos)) {
                return area;
            }
        }
        return null;
    }

    /** 指定维度内所有包含给定坐标的区域（区域允许重叠，渲染端需拿到全部候选再按权重决出）。 */
    public List<Area> findAt(int dim, Vec3d pos) {
        List<Area> result = new ArrayList<>();
        for (Area area : inDim(dim).values()) {
            if (area.contains(pos)) {
                result.add(area);
            }
        }
        return result;
    }

    /**
     * 找出与给定形状冲突（平局规则，方块级精确判定）的全部已存区域 id。
     * 见 {@link Area#weightConflict}：仅当"重叠且同种效果权重相等"时才视作冲突。
     */
    public List<Integer> conflictsIn(int dim, Area intent) {
        List<Integer> conflicts = new ArrayList<>();
        for (Map.Entry<Integer, Area> entry : inDim(dim).entrySet()) {
            if (intent.weightConflict(entry.getValue())) {
                conflicts.add(entry.getKey());
            }
        }
        return conflicts;
    }

    /** 该维度下是否已有区域与给定区域冲突（平局规则）。 */
    public boolean conflicts(int dim, Area intent) {
        for (Area area : inDim(dim).values()) {
            if (intent.weightConflict(area)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 新增区域：先做冲突判定，冲突则返回 null 且不占用 id；否则分配 id 入表并返回实例。
     */
    public Area add(int dim, AreaShape shape, float lightness, float duration) {
        Area area = new Area(shape, lightness, duration);
        if (conflicts(dim, area)) {
            return null;
        }
        area.id = allocateId();
        put(dim, area.id, area);
        return area;
    }

    /** 按 id 在全部维度中定位区域；不存在返回 null。 */
    public Located locate(int id) {
        for (Map.Entry<Integer, Map<Integer, Area>> entry : byDim.entrySet()) {
            Area area = entry.getValue().get(id);
            if (area != null) {
                return new Located(entry.getKey(), area);
            }
        }
        return null;
    }

    /** 区域及其所在维度。 */
    public static final class Located {

        public final int dim;
        public final Area area;

        Located(int dim, Area area) {
            this.dim = dim;
            this.area = area;
        }
    }
}
