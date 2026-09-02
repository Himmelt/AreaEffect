package org.soraworld.areaeffect.util;

/**
 * 集中管理跨版本反射用到的 srg 混淆名（func_/field_）。
 * <p>
 * 本项目以单 jar 运行在 1.7.10~1.12.2，dev 环境与运行时均以 srg 名链接，
 * 这里用可读的 mcp 语义作为常量名、srg 名作为值，便于阅读；若将来某 srg 名在某版本漂移，
 * 也只需在此处统一调整。
 * <p>
 * 注意：仅收录通过"反射以字符串方式"访问的成员；直接方法/字段调用的 srg 名是编译期符号，
 * 无法用字符串常量替换，故不在此列。
 */
public final class SrgNames {

    private SrgNames() {
    }

    /** 1.7 物品注册表静态字段（Item.field_150901_e / itemRegistry） */
    public static final String ITEM_REGISTRY = "field_150901_e";

    /** RegistryNamespaced#getObject(Object) */
    public static final String REGISTRY_GET_OBJECT = "func_82594_a";

    /** RegistryNamespaced#getNameForObject(Object)，旧版 1.7 映射名 */
    public static final String REGISTRY_GET_NAME_OLD = "func_148750_c";

    /** RegistryNamespaced#getNameForObject(Object)，新版 1.8+ 映射名 */
    public static final String REGISTRY_GET_NAME_NEW = "func_177774_c";

    /** 旧包路径的注册表类（1.7~1.10） */
    public static final String REGISTRY_CLASS_OLD = "net.minecraft.util.RegistryNamespaced";

    /** 新包路径的注册表类（1.11~1.12） */
    public static final String REGISTRY_CLASS_NEW = "net.minecraft.util.registry.RegistryNamespaced";

    /** 实体包围盒字段（Entity.field_70121_D / boundingBox） */
    public static final String ENTITY_BOUNDING_BOX = "field_70121_D";
}