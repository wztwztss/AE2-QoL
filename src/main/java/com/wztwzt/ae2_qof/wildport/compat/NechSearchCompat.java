/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import cpw.mods.fml.common.Loader;

public final class NechSearchCompat {

    private static Boolean available;
    private static Object api;
    private static Method contains;

    private NechSearchCompat() {}

    public static boolean matches(String text, String search) {
        String needle = normalize(search);
        if (needle.isEmpty()) {
            return true;
        }
        String haystack = normalize(text);
        if (haystack.contains(needle)) {
            return true;
        }
        return containsWithNech(text, search) || containsWithNech(haystack, needle);
    }

    private static boolean containsWithNech(String text, String search) {
        if (!isAvailable() || text == null || search == null) {
            return false;
        }
        try {
            Object result = contains.invoke(api, text, search);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            available = Boolean.FALSE;
            return false;
        }
    }

    private static boolean isAvailable() {
        if (available != null) {
            return available.booleanValue();
        }
        if (!Loader.isModLoaded("nech")) {
            available = Boolean.FALSE;
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("com.asdflj.nech.API");
            Field instance = apiClass.getField("INSTANCE");
            api = instance.get(null);
            contains = apiClass.getMethod("contains", String.class, CharSequence.class);
            available = Boolean.TRUE;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            available = Boolean.FALSE;
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
