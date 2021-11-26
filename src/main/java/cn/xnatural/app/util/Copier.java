package cn.xnatural.app.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;

import static cn.xnatural.app.Utils.iterateField;
import static cn.xnatural.app.Utils.iterateMethod;

/**
 * 对象拷贝工具
 * 支持:
 *  对象 转 map
 *  对象 转 javabean
 * @param <S> 源对象类型
 * @param <T> 目标对象类型
 */
public class Copier<S, T> {
    /**
     * 源对象
     */
    protected final S src;
    /**
     * 目标对象
     */
    protected final T target;
    /**
     * 忽略空属性
     * 默认不忽略空值属性
     */
    protected boolean ignoreNull = false;
    /**
     * 客外属性源 计算器
     */
    protected Map<String, BiFunction<S, T, Object>> valueGetter;
    /**
     * 属性值转换器配置
     * 源属性名 -> 值转换函数
     */
    protected Map<String, Function> valueConverter;
    /**
     * 属性别名
     * 目标属性名 -> 源属性名
     */
    protected Map<String, String> mapProps;
    /**
     * 忽略属性
     */
    protected final Set<String> ignore = new HashSet<>(Arrays.asList("class"));


    public Copier(S src, T target) {
        this.src = src;
        this.target = target;
    }


    /**
     * 忽略属性
     * @param propNames 属性名
     * @return {@link Copier}
     */
    public Copier<S, T> ignore(String... propNames) {
        if (propNames == null) return this;
        for (String name : propNames) ignore.add(name);
        return this;
    }

    /**
     * 包含 class 属性
     * @return {@link Copier}
     */
    public Copier<S, T> showClassProp() { ignore.remove("class"); return this; }

    /**
     * 属性名 映射
     * {@link #ignore} 也能控制别名
     * @param srcPropName 源属性名
     * @param targetPropName 目标对象属性名
     * @return {@link Copier}
     */
    public Copier<S, T> mapProp(String srcPropName, String targetPropName) {
        if (targetPropName == null || targetPropName.isEmpty()) throw new IllegalArgumentException("Param targetPropName required");
        if (srcPropName == null || srcPropName.isEmpty()) throw new IllegalArgumentException("Param srcPropName required");
        if (mapProps == null) mapProps = new HashMap<>(7);
        mapProps.put(targetPropName, srcPropName);
        mapProps.put(srcPropName, targetPropName);
        return this;
    }

    /**
     * 忽略空值属性
     * @param ignoreNull 是否忽略
     * @return {@link Copier}
     */
    public Copier<S, T> ignoreNull(boolean ignoreNull) { this.ignoreNull = ignoreNull; return this; }

    /**
     * 手动添加额外属性源
     * NOTE: 会优先于 源对象中的属性
     * @param srcPropName 属性源名
     * @param valuer 属性值计算器
     * @return {@link Copier}
     */
    public Copier<S, T> add(String srcPropName, Supplier<Object> valuer) {
        if (valuer == null) throw new IllegalArgumentException("Param valuer required");
        return add(srcPropName, (s, t) -> valuer.get());
    }

    /**
     * 手动添加额外属性源
     * NOTE: 会优先于 源对象中的属性
     * @param srcPropName 属性源名
     * @param valuer 属性值计算器
     * @return {@link Copier}
     */
    public Copier<S, T> add(String srcPropName, BiFunction<S, T, Object> valuer) {
        if (srcPropName == null || srcPropName.isEmpty()) throw new IllegalArgumentException("Param srcPropName required");
        if (valuer == null) throw new IllegalArgumentException("Param valuer required");
        if (valueGetter == null) valueGetter = new HashMap<>(7);
        valueGetter.put(srcPropName, valuer);
        return this;
    }

    /**
     * 添加属性值转换器
     * @param srcPropName 源属性名
     * @param converter 值转换器
     * @return {@link Copier}
     */
    public Copier<S, T> addConverter(String srcPropName, Function converter) {
        if (srcPropName == null || srcPropName.isEmpty()) throw new IllegalArgumentException("Param srcPropName required");
        if (converter == null) throw new IllegalArgumentException("Param converter required");
        if (valueConverter == null) valueConverter = new HashMap<>(7);
        valueConverter.put(srcPropName, converter);
        return this;
    }


    /**
     * 对象属性之前的copy
     * @return 目标对象
     */
    public T build() {
        if (target == null || src == null) return target;
        // map 转 map
        if (src instanceof Map && target instanceof Map) {
            // 属性赋值函数
            final Consumer<String> setFn = srcPropName -> {
                if (srcPropName == null || ignore.contains(srcPropName)) return;
                try {
                    String targetPropName = mapProps != null && mapProps.containsKey(srcPropName) ? mapProps.get(srcPropName) : srcPropName;
                    if (ignore.contains(targetPropName)) return;
                    Object v = get(srcPropName);
                    if (ignoreNull && v == null) return;
                    ((Map) target).put(targetPropName, v);
                } catch (Exception ex) { /** ignore **/ }
            };
            // 遍历源属性集map
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) src).entrySet()) {
                setFn.accept(entry.getKey().toString());
            }
            // 遍历 额外属性集
            valueGetter.forEach((propName, getter) -> setFn.accept(propName));
        }
        // javabean 转 map
        else if (target instanceof Map) javabeanToMap();
        // 转 javabean
        else toJavabean();
        return target;
    }


    protected void javabeanToMap() {
        final Set<String> alreadyNames = new HashSet<>(); //防止 getter 和 字段重复 设值
        // 属性赋值函数
        final Consumer<String> setFn = srcPropName -> {
            if (srcPropName == null || ignore.contains(srcPropName) || alreadyNames.contains(srcPropName)) return;
            alreadyNames.add(srcPropName);
            try {
                String targetPropName = mapProps != null && mapProps.containsKey(srcPropName) ? mapProps.get(srcPropName) : srcPropName;
                if (ignore.contains(targetPropName)) return;
                Object v = get(srcPropName);
                if (ignoreNull && v == null) return;
                ((Map) target).put(targetPropName, v);
            } catch (Exception ex) { /** ignore **/ }
        };
        // 遍历 getter
        iterateMethod(src.getClass(), method -> setFn.accept(getGetterName(method)));
        // 遍历 public 字段
        iterateField(src.getClass(), field -> setFn.accept(getPropFieldName(field)));
        // 遍历 额外属性集
        valueGetter.forEach((propName, getter) -> setFn.accept(propName));
    }


    protected void toJavabean() {
        final Set<String> alreadyNames = new HashSet<>(); //防止 setter 和 字段重复 设值
        // 属性赋值函数
        final BiConsumer<String, Consumer<Object>> setFn = (targetPropName, fn) -> {
            if (targetPropName == null || ignore.contains(targetPropName) || alreadyNames.contains(targetPropName)) return;
            alreadyNames.add(targetPropName);
            try {
                String srcPropName = mapProps != null && mapProps.containsKey(targetPropName) ? mapProps.get(targetPropName) : targetPropName;
                if (ignore.contains(srcPropName)) return;
                Object v = get(srcPropName);
                if (ignoreNull && v == null) return;
                fn.accept(v);
            } catch (Exception ex) { /** ignore **/ }
        };
        // 遍历 setter
        iterateMethod(target.getClass(), method -> {
            setFn.accept(getSetterName(method), (v) -> {
                try { method.setAccessible(true); method.invoke(target, v); } catch (Exception ex) { /** ignore **/ }
            });
        });
        // 遍历 public 字段
        iterateField(target.getClass(), field -> {
            setFn.accept(getPropFieldName(field), (v) -> {
                try { field.setAccessible(true); field.set(target, v); } catch (Exception ex) { /** ignore **/ }
            });
        });
    }


    /**
     * 属性取值函数
     * @param srcPropName 源属性名
     * @return 属性值
     * @throws Exception
     */
    protected Object get(String srcPropName) throws Exception {
        Object v = null;
        if (valueGetter != null && valueGetter.containsKey(srcPropName)) v = valueGetter.get(srcPropName).apply(src, target);
        else if (src instanceof Map) v = ((Map<?, ?>) src).get(srcPropName);
        else {
            Class c = src.getClass();
            out: do {
                for (Method m : c.getDeclaredMethods()) {
                    if (Objects.equals(getGetterName(m), srcPropName)) {
                        m.setAccessible(true);
                        v = m.invoke(src);
                        break out;
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    if (Objects.equals(getPropFieldName(f), srcPropName)) {
                        f.setAccessible(true);
                        v = f.get(src);
                        break out;
                    }
                }
                c = c.getSuperclass();
            } while (c != null);
        }
        if (valueConverter != null && valueConverter.containsKey(srcPropName)) {
            v = valueConverter.get(srcPropName).apply(v);
        }
        return v;
    }


    /**
     * 获取 setter 方法 属性名
     * @param method 方法
     * @return 属性名
     */
    protected String getSetterName(Method method) {
        boolean isSetter = method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterCount() == 1 &&
                Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers());
        if (isSetter) {
            String tmp = method.getName().replace("set", "");
            if (tmp.length() == 1) return tmp.toUpperCase();
            return Character.toLowerCase(tmp.charAt(0)) + tmp.substring(1);
        }
        return null;
    }


    /**
     * 获取 getter 方法 属性名
     * @param method 方法
     * @return 属性名
     */
    protected String getGetterName(Method method) {
        boolean isGetter = method.getName().startsWith("get") && method.getName().length() > 3 &&
                method.getParameterCount() == 0 && !void.class.equals(method.getReturnType()) &&
                Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers());
        if (isGetter) {
            String tmp = method.getName().replace("get", "");
            if (tmp.length() == 1) return tmp.toUpperCase();
            return Character.toLowerCase(tmp.charAt(0)) + tmp.substring(1);
        }
        return null;
    }


    /**
     * 如果是个属性字段就返回 字段名 作属性名
     * @param field 字段
     * @return 属性名
     */
    protected String getPropFieldName(Field field) {
        if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) return null;
        return field.getName();
    }
}
