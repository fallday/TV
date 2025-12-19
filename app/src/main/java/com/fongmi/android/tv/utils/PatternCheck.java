package com.fongmi.android.tv.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PatternCheck {
    // 匹配开头的正则（保留^）
    private static final String REGEX_START = "^https?://.*?/rtsp/";
    private static final Pattern PATTERN_START = Pattern.compile(REGEX_START);

    /**
     * 检测字符串开头是否匹配 http(s)://任意内容/rtsp/
     * @param str 待检测字符串
     * @return true=开头匹配，false=不匹配
     */
    public static boolean isStartWithPattern(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        Matcher matcher = PATTERN_START.matcher(str);
        // find() 检测是否存在匹配片段（因正则带^，仅开头匹配时返回true）
        return matcher.find();
        // 或等价写法：matcher.lookingAt()（专门检测开头匹配，无需^也可，但保留^更直观）
    }

    public static void main(String[] args) {
        // 测试用例
        String test1 = "http://192.168.1.5:4022/rtsp/abc"; // 开头匹配 ✅
        String test2 = "https://xxx:6666/rtsp/";          // 开头匹配 ✅
        String test3 = "abchttp://123/rtsp/def";          // 开头不匹配 ❌
        String test4 = "ftp://192.168.1.5/rtsp/";         // 协议不符 ❌

        System.out.println(isStartWithPattern(test1)); // true
        System.out.println(isStartWithPattern(test2)); // true
        System.out.println(isStartWithPattern(test3)); // false
        System.out.println(isStartWithPattern(test4)); // false
    }
}