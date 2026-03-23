package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.ResolvedArthasPackage;
import com.alibaba.arthas.idea.workbench.util.DigestUtils;
import com.alibaba.arthas.idea.workbench.util.FileToolkit;
import com.intellij.openapi.components.Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 负责按照不同包来源解析、下载并缓存 Arthas 发行包。
 */
@Service(Service.Level.APP)
public final class ArthasPackageService {

    private static final String OFFICIAL_LATEST_ZIP_URL =
            "https://arthas.aliyun.com/download/latest_version?mirror=aliyun";
    private static final String PLUGIN_HOME_DIR = ".arthas-workbench-plugin";
    private static final String PACKAGE_CACHE_DIR = "packages";

    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    /**
     * 将抽象的包来源规格解析为本地可直接使用的 Arthas 包。
     */
    public ResolvedArthasPackage resolve(PackageSourceSpec source, boolean forceUpdate) {
        Objects.requireNonNull(source, "source");
        return switch (source.getType()) {
            case OFFICIAL_LATEST ->
                resolveRemoteZip(
                        source,
                        message("service.package.label.official_latest"),
                        "official-latest",
                        OFFICIAL_LATEST_ZIP_URL,
                        forceUpdate);
            case OFFICIAL_VERSION -> {
                String version = source.getValue().trim();
                require(!version.isBlank(), message("service.package.validation.version_required"));
                yield resolveRemoteZip(
                        source,
                        message("service.package.label.official_version", version),
                        "official-version-" + DigestUtils.sha256(version),
                        "https://github.com/alibaba/arthas/releases/download/arthas-all-" + version + "/arthas-bin.zip",
                        forceUpdate);
            }
            case CUSTOM_REMOTE_ZIP -> {
                String url = source.getValue().trim();
                require(
                        url.startsWith("http://") || url.startsWith("https://"),
                        message("service.package.validation.remote_zip_url"));
                yield resolveRemoteZip(
                        source,
                        message("service.package.label.remote_zip", url),
                        "custom-remote-" + DigestUtils.sha256(url),
                        url,
                        forceUpdate);
            }
            case LOCAL_ZIP -> {
                Path zipPath = Path.of(source.getValue().trim());
                require(Files.isRegularFile(zipPath), message("service.package.validation.local_zip_missing", zipPath));
                Path cacheDir = cacheRoot()
                        .resolve("local-zip-"
                                + DigestUtils.sha256(zipPath.toAbsolutePath().toString()));
                Path extractionDir = cacheDir.resolve("home");
                try {
                    if (forceUpdate || !Files.exists(extractionDir)) {
                        FileToolkit.deleteRecursively(extractionDir);
                        Files.createDirectories(cacheDir);
                        FileToolkit.unzip(zipPath, extractionDir);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(
                            message("service.package.error.local_zip_extract", zipPath), exception);
                }
                Path bootJar = findBootJar(extractionDir);
                require(bootJar != null, message("service.package.validation.boot_missing_in_zip", zipPath));
                yield new ResolvedArthasPackage(
                        source,
                        message("service.package.label.local_zip", zipPath.getFileName()),
                        bootJar,
                        bootJar.getParent());
            }
            case LOCAL_PATH -> {
                Path path = Path.of(source.getValue().trim());
                require(Files.exists(path), message("service.package.validation.local_path_missing", path));
                require(Files.isDirectory(path), message("service.package.validation.local_dir_required", path));
                Path bootJar = findBootJar(path);
                require(bootJar != null, message("service.package.validation.boot_missing_in_dir", path));
                yield new ResolvedArthasPackage(
                        source,
                        message("service.package.label.local_dir", path.getFileName()),
                        bootJar,
                        bootJar.getParent());
            }
        };
    }

    private ResolvedArthasPackage resolveRemoteZip(
            PackageSourceSpec source, String label, String cacheKey, String url, boolean forceUpdate) {
        Path cacheDir = cacheRoot().resolve(cacheKey);
        Path zipPath = cacheDir.resolve("arthas-bin.zip");
        Path extractionDir = cacheDir.resolve("home");
        try {
            if (forceUpdate || !Files.exists(zipPath)) {
                Files.createDirectories(cacheDir);
                downloadToFile(url, zipPath);
            }
            if (forceUpdate || !Files.exists(extractionDir)) {
                FileToolkit.deleteRecursively(extractionDir);
                Files.createDirectories(extractionDir);
                FileToolkit.unzip(zipPath, extractionDir);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(message("service.package.error.prepare_failed", label), exception);
        }

        Path bootJar = findBootJar(extractionDir);
        require(bootJar != null, message("service.package.validation.download_boot_missing", url));
        return new ResolvedArthasPackage(source, label, bootJar, bootJar.getParent());
    }

    private Path cacheRoot() {
        Path root = defaultCacheRoot(Path.of(System.getProperty("user.home")));
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException(message("service.package.error.cache_dir", root), exception);
        }
        return root;
    }

    static Path defaultCacheRoot(Path userHome) {
        Objects.requireNonNull(userHome, "userHome");
        return userHome.resolve(PLUGIN_HOME_DIR).resolve(PACKAGE_CACHE_DIR);
    }

    private void downloadToFile(String url, Path target) throws IOException {
        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message("service.package.error.download_interrupted", url), exception);
        }
        require(
                response.statusCode() >= 200 && response.statusCode() <= 299,
                message("service.package.error.download_status", response.statusCode(), url));
        try (InputStream input = response.body()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path findBootJar(Path root) {
        try (Stream<Path> stream = Files.walk(root, 4)) {
            return stream.filter(path -> Files.isRegularFile(path)
                            && "arthas-boot.jar".equals(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            throw new IllegalStateException(message("service.package.error.scan_failed", root), exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
