package com.sunlc.dsp.engine.function;

import java.util.HashMap;
import java.util.Map;

public class FunctionRegistry {

    private static final Map<String, FunctionInvoker> FUNCTIONS = new HashMap<>();

    static {
        register("DATE_FORMAT", (params) -> {
            if (params.length < 2) return params[0];
            try {
                String value = String.valueOf(params[0]);
                String pattern = String.valueOf(params[1]);
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(value,
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.format(java.time.format.DateTimeFormatter.ofPattern(pattern));
            } catch (Exception e) {
                return params[0];
            }
        });

        register("DATE_ADD", (params) -> {
            if (params.length < 2) return params[0];
            try {
                String dateStr = String.valueOf(params[0]);
                int days = Integer.parseInt(String.valueOf(params[1]));
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
                return date.plusDays(days).toString();
            } catch (Exception e) {
                return params[0];
            }
        });

        register("DATE_SUB", (params) -> {
            if (params.length < 2) return params[0];
            try {
                String dateStr = String.valueOf(params[0]);
                int days = Integer.parseInt(String.valueOf(params[1]));
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
                return date.minusDays(days).toString();
            } catch (Exception e) {
                return params[0];
            }
        });

        register("CONCAT", (params) -> {
            StringBuilder sb = new StringBuilder();
            for (Object p : params) {
                if (p != null) sb.append(p);
            }
            return sb.toString();
        });

        register("SUBSTRING", (params) -> {
            if (params.length < 2) return params[0];
            String str = String.valueOf(params[0]);
            int start = Integer.parseInt(String.valueOf(params[1]));
            int end = params.length >= 3 ? Integer.parseInt(String.valueOf(params[2])) : str.length();
            return str.substring(start, Math.min(end, str.length()));
        });

        register("TRIM", (params) -> params[0] != null ? String.valueOf(params[0]).trim() : null);
        register("REPLACE", (params) -> {
            if (params.length < 3) return params[0];
            return String.valueOf(params[0]).replace(String.valueOf(params[1]), String.valueOf(params[2]));
        });
        register("UPPER", (params) -> params[0] != null ? String.valueOf(params[0]).toUpperCase() : null);
        register("LOWER", (params) -> params[0] != null ? String.valueOf(params[0]).toLowerCase() : null);

        register("TYPE_CONVERT", (params) -> {
            if (params.length < 2) return params[0];
            String target = String.valueOf(params[1]).toUpperCase();
            Object value = params[0];
            try {
                switch (target) {
                    case "STRING": return String.valueOf(value);
                    case "INTEGER": return Integer.parseInt(String.valueOf(value));
                    case "LONG": return Long.parseLong(String.valueOf(value));
                    case "DOUBLE": return Double.parseDouble(String.valueOf(value));
                    default: return value;
                }
            } catch (Exception e) {
                return value;
            }
        });

        register("JSON_EXTRACT", (params) -> {
            if (params.length < 2) return params[0];
            try {
                cn.hutool.json.JSON json = cn.hutool.json.JSONUtil.parse(params[0]);
                return json.getByPath(String.valueOf(params[1]));
            } catch (Exception e) {
                return params[0];
            }
        });

        register("NVL", (params) -> {
            for (Object p : params) {
                if (p != null) return p;
            }
            return null;
        });

        register("IFF", (params) -> {
            if (params.length < 3) return params[0];
            boolean condition = Boolean.parseBoolean(String.valueOf(params[0]));
            return condition ? params[1] : params[2];
        });

        // ==================== 日期函数 ====================

        register("WORKDAYS", (params) -> {
            if (params.length < 2) return 0;
            try {
                java.time.LocalDate start = java.time.LocalDate.parse(String.valueOf(params[0]));
                java.time.LocalDate end = java.time.LocalDate.parse(String.valueOf(params[1]));
                long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                if (days < 0) { java.time.LocalDate tmp = start; start = end; end = tmp; days = -days; }
                long workdays = 0;
                java.time.LocalDate cur = start;
                while (!cur.isAfter(end)) {
                    java.time.DayOfWeek dow = cur.getDayOfWeek();
                    if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workdays++;
                    cur = cur.plusDays(1);
                }
                return workdays;
            } catch (Exception e) {
                return 0;
            }
        });

        // ==================== 字符串函数 ====================

        register("LIKE_MATCH", (params) -> {
            if (params.length < 2) return false;
            String text = String.valueOf(params[0]);
            String pattern = String.valueOf(params[1]);
            // 将SQL LIKE模式转为正则: % → .*, _ → .
            String regex = "^" + pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".") + "$";
            return text.matches(regex);
        });

        register("CONCAT_WS", (params) -> {
            if (params.length < 2) return "";
            String separator = String.valueOf(params[0]);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                if (params[i] != null) {
                    if (sb.length() > 0) sb.append(separator);
                    sb.append(params[i]);
                }
            }
            return sb.toString();
        });

        register("REGEX_MATCH", (params) -> {
            if (params.length < 2) return false;
            try {
                String text = String.valueOf(params[0]);
                String regex = String.valueOf(params[1]);
                return text.matches(regex);
            } catch (Exception e) {
                return false;
            }
        });

        register("LENGTH", (params) -> {
            if (params.length < 1 || params[0] == null) return 0;
            return String.valueOf(params[0]).length();
        });

        register("PAD_LEFT", (params) -> {
            if (params.length < 3) return params[0];
            String str = String.valueOf(params[0]);
            int len = Integer.parseInt(String.valueOf(params[1]));
            String pad = String.valueOf(params[2]);
            while (str.length() < len) str = pad + str;
            return str.length() > len ? str.substring(str.length() - len) : str;
        });

        register("PAD_RIGHT", (params) -> {
            if (params.length < 3) return params[0];
            String str = String.valueOf(params[0]);
            int len = Integer.parseInt(String.valueOf(params[1]));
            String pad = String.valueOf(params[2]);
            while (str.length() < len) str = str + pad;
            return str.length() > len ? str.substring(0, len) : str;
        });

        // ==================== 空值函数 ====================

        register("IFNULL", (params) -> {
            if (params.length < 2) return params[0];
            return params[0] != null ? params[0] : params[1];
        });

        // ==================== 聚合函数 ====================

        register("SUM", (params) -> {
            double sum = 0;
            for (Object p : params) {
                if (p != null) { try { sum += Double.parseDouble(String.valueOf(p)); } catch (Exception ignored) {} }
            }
            return sum;
        });

        register("AVG", (params) -> {
            double sum = 0;
            int count = 0;
            for (Object p : params) {
                if (p != null) { try { sum += Double.parseDouble(String.valueOf(p)); count++; } catch (Exception ignored) {} }
            }
            return count > 0 ? sum / count : 0;
        });

        register("COUNT", (params) -> {
            int count = 0;
            for (Object p : params) { if (p != null) count++; }
            return count;
        });

        register("MAX", (params) -> {
            if (params.length == 0) return null;
            Object max = null;
            for (Object p : params) {
                if (p != null) {
                    if (max == null) { max = p; }
                    else {
                        try {
                            if (Double.parseDouble(String.valueOf(p)) > Double.parseDouble(String.valueOf(max))) max = p;
                        } catch (Exception e) {
                            if (String.valueOf(p).compareTo(String.valueOf(max)) > 0) max = p;
                        }
                    }
                }
            }
            return max;
        });

        register("MIN", (params) -> {
            if (params.length == 0) return null;
            Object min = null;
            for (Object p : params) {
                if (p != null) {
                    if (min == null) { min = p; }
                    else {
                        try {
                            if (Double.parseDouble(String.valueOf(p)) < Double.parseDouble(String.valueOf(min))) min = p;
                        } catch (Exception e) {
                            if (String.valueOf(p).compareTo(String.valueOf(min)) < 0) min = p;
                        }
                    }
                }
            }
            return min;
        });

        // ==================== 数学函数 ====================

        register("ROUND", (params) -> {
            if (params.length < 1 || params[0] == null) return null;
            try {
                double val = Double.parseDouble(String.valueOf(params[0]));
                int scale = params.length >= 2 ? Integer.parseInt(String.valueOf(params[1])) : 0;
                return Math.round(val * Math.pow(10, scale)) / Math.pow(10, scale);
            } catch (Exception e) {
                return params[0];
            }
        });

        register("CEIL", (params) -> {
            if (params.length < 1 || params[0] == null) return null;
            try { return (long) Math.ceil(Double.parseDouble(String.valueOf(params[0]))); }
            catch (Exception e) { return params[0]; }
        });

        register("FLOOR", (params) -> {
            if (params.length < 1 || params[0] == null) return null;
            try { return (long) Math.floor(Double.parseDouble(String.valueOf(params[0]))); }
            catch (Exception e) { return params[0]; }
        });
    }

    public static void register(String name, FunctionInvoker invoker) {
        FUNCTIONS.put(name.toUpperCase(), invoker);
    }

    public static Object invoke(String name, Object... params) {
        FunctionInvoker invoker = FUNCTIONS.get(name.toUpperCase());
        if (invoker == null) {
            throw new RuntimeException("函数不存在: " + name);
        }
        return invoker.invoke(params);
    }

    public static boolean exists(String name) {
        return FUNCTIONS.containsKey(name.toUpperCase());
    }

    @FunctionalInterface
    public interface FunctionInvoker {
        Object invoke(Object... params);
    }
}
