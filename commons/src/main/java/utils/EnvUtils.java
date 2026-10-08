package utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 环境变量加载工具类
 * 从 classpath 下的 .env 文件加载配置到系统属性
 * 供所有 agent 模块在启动时调用
 */
public class EnvUtils {

    private static volatile boolean loaded = false;

    /**
     * 从 classpath 加载 .env 文件到系统属性（仅加载一次）
     */
    public static void loadEnv() {
        if (loaded) {
            return;
        }
        synchronized (EnvUtils.class) {
            if (loaded) {
                return;
            }
            try (InputStream is = openEnv()) {
                if (is == null) {
                    System.out.println("[WARN] 未找到 .env 文件，请设置所需环境变量");
                    loaded = true;
                    return;
                }
                Properties props = new Properties();
                props.load(is);
                props.forEach((key, value) -> {
                    String v = value.toString().trim();
                    if ((v.startsWith("'") && v.endsWith("'"))
                            || (v.startsWith("\"") && v.endsWith("\""))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if (System.getenv(key.toString()) == null) {
                        System.setProperty(key.toString(), v);
                    }
                });
                System.out.println("[INFO] 已从 .env 文件加载 " + props.size() + " 个配置项");
            } catch (Exception e) {
                System.err.println("[ERROR] 加载 .env 文件失败: " + e.getMessage());
            }
            loaded = true;
        }
    }

    private static InputStream openEnv() throws java.io.IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(".env");
            if (Files.isRegularFile(candidate)) return Files.newInputStream(candidate);
            directory = directory.getParent();
        }
        return EnvUtils.class.getClassLoader().getResourceAsStream(".env");
    }
}
