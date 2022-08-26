package com.github.houkunlin.util;

import com.github.houkunlin.model.TableColumnType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 插件工具类
 *
 * @author HouKunLin
 * @date 2020/11/17 0017 16:32
 */
@Getter
public class PluginUtils {
    public static final String PLUGIN_ID = "com.github.houkunlin.database.generator";
    public static final String CONFIG_DIR = "config";
    public static final String TEMPLATE_DIR = "templates";
    public static final String PROJECT_WORK_DIR = "generator";
    /**
     * 当前项目对象
     */
    private static Project project;
    /**
     * 字段类型映射
     */
    private static TableColumnType[] columnTypes;
    /**
     * 全局的草稿文件和控制台-扩展 路径： ${PathManager.getConfigPath}/extensions/${PLUGIN_ID}
     */
    private static File extensionPluginDir;
    /**
     * 当前项目下的插件路径： ${project.dir}/${PROJECT_WORK_DIR}
     */
    private static File projectPluginDir;
    /**
     * 当前项目下的idea配置路径下的插件路径： ${project.dir}/.idea/${PROJECT_WORK_DIR}
     */
    private static File projectWorkspacePluginDir;

    private PluginUtils() {
    }

    public static Project getProject() {
        return project;
    }

    public static File getExtensionPluginDir() {
        return extensionPluginDir;
    }

    public static File getProjectPluginDir() {
        return projectPluginDir;
    }

    public static File getProjectWorkspacePluginDir() {
        return projectWorkspacePluginDir;
    }

    public static void setProject(Project project) {
        PluginUtils.project = project;

        extensionPluginDir = new File(PathManager.getScratchPath(), "extensions/" + PLUGIN_ID);
        projectPluginDir = new File(project.getBasePath(), PROJECT_WORK_DIR);
        projectWorkspacePluginDir = new File(project.getBasePath(), ".idea/" + PROJECT_WORK_DIR);

        mkdirs(extensionPluginDir);
    }

    public static File getExtensionPluginDirFile(String relativeFilepath) {
        return new File(extensionPluginDir, relativeFilepath);
    }

    public static File getProjectPluginDirFile(String relativeFilepath) {
        return new File(projectPluginDir, relativeFilepath);
    }

    public static File getProjectWorkspacePluginDirFile(String relativeFilepath) {
        return new File(projectWorkspacePluginDir, relativeFilepath);
    }

    /**
     * 创建目录信息
     *
     * @param path 路径
     */
    private static void mkdirs(File path) {
        if (!path.exists()) {
            if (!path.mkdirs()) {
                Messages.showMessageDialog("创建\"" + path + "\"路径失败", "创建路径失败", Messages.getErrorIcon());
            }
        }
    }

    /**
     * 获取配置文件
     *
     * @param clazz 配置文件对象
     * @param <T>   泛型
     * @return 配置文件对象
     */
    public static <T> T getConfig(Class<T> clazz) {
        String filename = clazz.getSimpleName().toLowerCase();
        try {
            return getConfig(clazz, filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取配置文件
     *
     * @param clazz          配置文件对象
     * @param configFileName 配置文件名称（实际为 class 的名称，与文件名称一一对应）
     * @param <T>            泛型
     * @return 配置文件对象
     * @throws Exception 异常
     */
    public static <T> T getConfig(Class<T> clazz, String configFileName) throws Exception {
        String resource = "config/" + configFileName + ".json";
        final File file = getConfigFile(resource);
        if (file != null) {
            // 此时 file 一定是存在的
            try {
                return JsonUtils.parse(clazz, new FileInputStream(file));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            // 项目文件中不存在配置文件，尝试从插件内部读取配置文件
            return JsonUtils.parse(clazz, resource);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * 获得配置文件
     *
     * @param resource 配置文件名称（相对插件路径的名称，带 config 前缀）
     * @return 配置文件
     */
    private static File getConfigFile(String resource) {
        File file = new File(projectWorkspacePluginDir, resource);
        if (file.exists()) {
            return file;
        }
        file = new File(projectPluginDir, resource);
        if (file.exists()) {
            return file;
        }
        file = new File(extensionPluginDir, resource);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    /**
     * 获取数据库字段类型映射配置
     *
     * @return 数据库字段类型映射配置
     */
    public static TableColumnType[] getTableColumnTypes() {
        try {
            return getConfig(TableColumnType[].class, "types");
        } catch (Exception e) {
            e.printStackTrace();
            Messages.showMessageDialog(e.getMessage(), "解析默认类型映射配置失败(严重影响功能)", Messages.getErrorIcon());
            return new TableColumnType[0];
        }
    }

    /**
     * 获得数据库与Java映射配置
     */
    public static TableColumnType[] getColumnTypes() {
        if (columnTypes == null) {
            columnTypes = getTableColumnTypes();
        }
        return columnTypes;
    }

    /**
     * 刷新项目
     */
    public static void refreshProject() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "刷新项目空间 ...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Consumer<VirtualFile> refresh = (virtualFile) -> {
                    if (virtualFile != null) {
                        virtualFile.refresh(false, true);
                    }
                };
                refresh.accept(LocalFileSystem.getInstance().findFileByPath(Objects.requireNonNull(project.getBasePath())));
                refresh.accept(project.getProjectFile());
                refresh.accept(project.getWorkspaceFile());
            }
        });
    }

    /**
     * 刷新项目
     */
    public static void refreshWorkspace() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "刷新插件工作空间 ...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(PluginUtils.getExtensionPluginDir());
                if (virtualFile != null) {
                    virtualFile.refresh(false, true);
                }
            }
        });
    }

    /**
     * 同步插件内部的代码模板、配置文件
     */
    public static void syncResources() {
        try {
            new SyncResources().run();
        } catch (Exception error) {
            error.printStackTrace();
            Messages.showErrorDialog("插件初始化错误，可能导致无法使用，主要涉及到插件配置 JSON 文件和插件代码模板文件。\n\n" + error.getMessage(), "初始化错误");
        }
    }
}
