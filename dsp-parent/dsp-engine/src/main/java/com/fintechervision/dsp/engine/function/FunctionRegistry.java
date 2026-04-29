package com.fintechervision.dsp.engine.function;

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
