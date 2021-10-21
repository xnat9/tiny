package cn.xnatural.app.util;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

import static cn.xnatural.app.Utils.iterateField;
import static cn.xnatural.app.Utils.iterateMethod;

/**
 * 转换成Map
 * use {@link Copier}
 * @param <T>
 */
@Deprecated
public class ToMap<T> {
    private T                                  bean;
    private Map<String, String> propAlias;
    private final Set<String> ignore = new HashSet<>(Arrays.asList("class"));
    private Map<String, Function>              valueConverter;
    private Map<String, Map<String, Function>> newProp;
    private boolean                            ignoreNull = false;// 默认不忽略空值属性
    private Comparator<String> comparator;
    private Map<String, Object>                result; //结果map
    private Map<String, Object>                extraAttrs; //手动添加的额外的属性

    public ToMap(T bean) { this.bean = bean; }
    public ToMap<T> aliasProp(String originPropName, String aliasName) {
        if (originPropName == null || originPropName.isEmpty() || originPropName.trim().isEmpty()) throw new IllegalArgumentException("Param originPropName not empty");
        if (aliasName == null || aliasName.isEmpty() || aliasName.trim().isEmpty()) throw new IllegalArgumentException("Param aliasName not empty");
        if (propAlias == null) propAlias = new HashMap<>(7);
        propAlias.put(originPropName, aliasName.trim());
        return this;
    }
    public ToMap<T> showClassProp() { ignore.remove("class"); return this; }
    public ToMap<T> ignoreNull(boolean ignoreNull) { this.ignoreNull = ignoreNull; return this; }
    public ToMap<T> ignoreNull() { this.ignoreNull = true; return this; }
    public ToMap<T> sort(Comparator<String> comparator) { this.comparator = comparator; return this; }
    public ToMap<T> sort() { this.comparator = Comparator.naturalOrder(); return this; }
    public ToMap<T> ignore(String... propNames) {
        if (propNames == null) return this;
        Collections.addAll(ignore, propNames);
        return this;
    }

    /**
     * 值转换
     * @param propName 属性名
     * @param converter 值转换器
     * @return {@link ToMap}
     */
    public ToMap<T> addConverter(String propName, Function converter) {
        if (propName == null || propName.isEmpty() || propName.trim().isEmpty()) throw new IllegalArgumentException("Param propName not empty");
        if (converter == null) throw new IllegalArgumentException("Param converter required");
        if (valueConverter == null) valueConverter = new HashMap<>(7);
        valueConverter.put(propName, converter);
        return this;
    }

    /**
     * 值转换
     * @param originPropName 原属性名
     * @param newPropName 新属性名
     * @param converter 值转换器
     * @return {@link ToMap}
     */
    public ToMap<T> addConverter(String originPropName, String newPropName, Function converter) {
        if (originPropName == null || originPropName.isEmpty() || originPropName.trim().isEmpty()) throw new IllegalArgumentException("Param originPropName not empty");
        if (newPropName == null || newPropName.isEmpty() || newPropName.trim().isEmpty()) throw new IllegalArgumentException("Param newPropName not empty");
        if (converter == null) throw new IllegalArgumentException("Param converter required");
        if (newProp == null) { newProp = new HashMap<>(); }
        newProp.computeIfAbsent(originPropName, s -> new HashMap<>(7)).put(newPropName, converter);
        return this;
    }

    /**
     * 手动添加属性
     * @param pName 属性名
     * @param value 属性值
     * @return {@link ToMap}
     */
    public ToMap<T> add(String pName, Object value) {
        if (extraAttrs == null) extraAttrs = new HashMap<>();
        extraAttrs.put(pName, value);
        return this;
    }


    /**
     * 填充属性
     * @param pName 属性名
     * @param originValue 属性原始值
     */
    protected void fill(String pName, Object originValue) {
        if (result == null) throw new IllegalArgumentException("Please after build");
        if (valueConverter != null && valueConverter.containsKey(pName)) {
            Object v = valueConverter.get(pName).apply(originValue);
            if (!(v == null && ignoreNull)) {result.put(pName, v);}
        } else if (newProp != null && newProp.get(pName) != null) {
            if (!(originValue == null && ignoreNull) && !ignore.contains(pName)) result.put(pName, originValue);
            for (Iterator<Map.Entry<String, Function>> it = newProp.get(pName).entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Function> e = it.next();
                Object v = e.getValue().apply(originValue);
                if (!(v == null && ignoreNull) && !ignore.contains(pName)) {result.put(e.getKey(), v);}
            }
        } else if (!(originValue == null && ignoreNull)) result.put(pName, originValue);
    }


    /**
     * 开始构建 结果Map
     * @return 结果Map
     */
    public Map<String, Object> build() {
        result = comparator != null ? new TreeMap<>(comparator) : new LinkedHashMap();
        if (bean == null) return result;
        Function<String, String> nameValid = pName -> {
            if (propAlias != null && propAlias.get(pName) != null) pName = propAlias.get(pName);
            if (ignore.contains(pName)) return null;
            return pName;
        };
        iterateMethod(bean.getClass(), method -> { // 遍历getter属性
            try {
                if (!void.class.equals(method.getReturnType()) && method.getName().startsWith("get") &&
                        method.getParameterCount() == 0 && !"getMetaClass".equals(method.getName()) && !Modifier.isStatic(method.getModifiers())) { // 属性
                    String tmp = method.getName().replace("get", "");
                    String pName = Character.toLowerCase(tmp.charAt(0)) + tmp.substring(1);
                    method.setAccessible(true);
                    pName = nameValid.apply(pName);
                    if (pName != null) fill(pName, method.invoke(bean));
                }
            } catch (Exception e) { /** ignore **/ }
        });

        iterateField(bean.getClass(), field -> { // 遍历字段属性
            if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) return;
            if (!(String.class.equals(field.getType()) || Number.class.isAssignableFrom(field.getType()) ||
                    URL.class.equals(field.getType()) || URI.class.equals(field.getType())
            )) return; //只输出普通属性
            String pName = field.getName();
            if (result.containsKey(pName) || pName.contains("$")) return; //去掉 名字包含 $ 的字段
            pName = nameValid.apply(pName);
            if (pName == null) return;
            try {
                field.setAccessible(true);
                fill(pName, field.get(bean));
            } catch (Exception e) { /** ignore **/ }
        });
        if (extraAttrs != null) extraAttrs.forEach((pName, o) -> {
            pName = nameValid.apply(pName);
            if (pName == null) return;
            fill(pName, o);
        });
        return result;
    }
}
