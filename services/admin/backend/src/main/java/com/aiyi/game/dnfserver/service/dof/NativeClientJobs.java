package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Durable, single-writer Linux candidate builds. Never installs or modifies a client/server.
 * Inputs are a version-locked private template, a public game identity and two exact binaries.
 * No arbitrary directory, executable command or source filename is accepted from HTTP.
 */
public final class NativeClientJobs implements AutoCloseable {
    private static final List<String> INPUTS = Collections.unmodifiableList(Arrays.asList(
        "template.dll", "game-public.der", "dof-launcher.exe", "ijl15.dll"));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_INPUT = 64L * 1024 * 1024;
    static final long BASE_RESERVE = 2L * 1024 * 1024 * 1024;
    // Two <=64 MiB binaries, generated config/background, ZIP overhead and metadata.
    static final long BUILD_PEAK = 3 * MAX_INPUT;
    interface SpaceProbe { long usable(Path path) throws IOException; }
    private final SpaceProbe spaceProbe;
    private final Path root, inputs, jobs;
    private final String instance;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ExecutorService worker;
    interface ScriptResolver { NativeScriptJobs.Artifact resolve(String id,String revision) throws IOException; }
    private final ScriptResolver scriptResolver;

    public static final class Binding {
        public String instanceId, environment, adapterVersion, databaseUuid, readerRecipe;
        public int clientFormatVersion;
        public Map<String,String> sha256;
    }
    public static final class Request {
        public String requestId, revision, endpoint, backgroundBase64, scriptTaskId;
    }
    public static final class Job {
        public String requestId, instanceId, revision, endpoint, backgroundSha256, state, createdAt, finishedAt, error, sha256;
        public long actor, bytes;
        public int progress, clientFormatVersion;
        public boolean nativeClientAcceptance = false;
        public String packageKind = "isolated-launcher-update-candidate";
        public String scriptTaskId,scriptSha256;
        public long scriptBytes;
    }
    private static DofFailure failure(int status, String message) { return new DofFailure(status, message); }
    public NativeClientJobs(Path workspace, String instance) throws IOException {
        this(workspace, instance, path -> Files.getFileStore(path).getUsableSpace());
    }
    NativeClientJobs(Path workspace, String instance, SpaceProbe spaceProbe) throws IOException {
        this(workspace,instance,spaceProbe,null);
    }
    NativeClientJobs(Path workspace,String instance,SpaceProbe spaceProbe,ScriptResolver scriptResolver)throws IOException {
        this.instance = instance;
        this.scriptResolver = scriptResolver;
        this.spaceProbe = Objects.requireNonNull(spaceProbe);
        Path absolute = workspace.toAbsolutePath().normalize();
        // Deployment must supply a real private directory. Never traverse a symlinked ancestor.
        if (!absolute.equals(absolute.toRealPath())) throw failure(409, "发布目录不能通过符号链接访问");
        root = absolute; inputs = directory(root.resolve("inputs")); jobs = directory(root.resolve("jobs"));
        Path lockPath = root.resolve("worker.lock");
        if (Files.isSymbolicLink(lockPath)) throw failure(409, "发布锁文件异常");
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { lockChannel.close(); throw failure(409, "发布目录已有运行中的任务服务"); }
        if (acquired == null) { lockChannel.close(); throw failure(409, "发布目录已被其他任务服务占用"); }
        lock = acquired;
        try {
            source();
            // A restarted process cannot claim that an unfinished archive is complete.
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(jobs)) {
                for (Path path : paths) {
                    if (!validId(path.getFileName().toString()) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    Job job = read(path.getFileName().toString());
                    if (Arrays.asList("QUEUED", "RUNNING").contains(job.state)) {
                        job.state = "INTERRUPTED"; job.error = "服务在任务完成前中断；旧包未改动，请核对后新建任务";
                        job.finishedAt = Instant.now().toString(); write(job);
                    }
                }
            }
        } catch (IOException | RuntimeException e) { lock.release(); lockChannel.close(); throw e; }
        worker = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "dof-client-package"); thread.setDaemon(true); return thread; });
    }
    private static Path directory(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw failure(409, "发布目录结构异常");
        return path;
    }
    private static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
    private static byte[] bounded(Path path, long max) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > max) throw failure(409, "发布输入缺失、超限或不是普通文件");
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int n;
            while ((n = in.read(buffer)) != -1) {
                if ((long)out.size() + n > max) throw failure(409, "发布输入超过限制");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
    private Binding source() throws IOException {
        Binding binding = JSON.readValue(bounded(inputs.resolve("binding.json"), 16384), Binding.class);
        if (!instance.equals(binding.instanceId) || !"isolated".equals(binding.environment)
            || !DofAdminService.VERSION.equals(binding.adapterVersion)
            || binding.clientFormatVersion != NativeClientFormat.VERSION || binding.sha256 == null
            || !validId(binding.databaseUuid) || !"dof-launcher-reader-2.1".equals(binding.readerRecipe)
            || !binding.sha256.keySet().equals(new HashSet<>(INPUTS))) throw failure(409, "发布来源未绑定当前隔离实例或版本");
        for (String key : INPUTS) if (binding.sha256.get(key) == null || !binding.sha256.get(key).matches("[0-9a-f]{64}")) throw failure(409, "发布来源校验值缺失");
        return binding;
    }
    private String revision(Binding binding) {
        StringBuilder wire = new StringBuilder(instance).append('\n').append(DofAdminService.VERSION).append('\n').append(NativeClientFormat.VERSION);
        wire.append('\n').append(binding.databaseUuid).append('\n').append(binding.readerRecipe);
        for (String key : INPUTS) wire.append('\n').append(key).append(':').append(binding.sha256.get(key));
        return NativeUiState.sha256(wire.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    synchronized void requireDatabase(String databaseUuid) throws IOException {
        if (databaseUuid == null || !databaseUuid.equals(source().databaseUuid))
            throw failure(409, "客户端发布材料与当前数据库实例不匹配");
    }
    private void requireSpace(long additionalPeak) throws IOException {
        long usable = spaceProbe.usable(root), required = BASE_RESERVE + additionalPeak;
        if (usable < required) throw failure(507, "发布目录空间不足：可用 " + usable
            + " 字节，需要 " + required + " 字节（2 GiB 基础余量加本步峰值）；旧包未改动");
    }
    private byte[] verified(Binding binding, String name) throws IOException {
        long bound = "game-public.der".equals(name) ? 4096 : "template.dll".equals(name) ? 1024 * 1024 : MAX_INPUT;
        byte[] bytes = bounded(inputs.resolve(name), bound);
        if (!NativeUiState.sha256(bytes).equals(binding.sha256.get(name))) throw failure(409, "发布输入发生变化，请重新准备并核对来源");
        return bytes;
    }
    public synchronized Map<String,Object> status() throws IOException {
        Binding binding = source(); Map<String,Object> out = new LinkedHashMap<>();
        try {
            PublicKey identity = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(verified(binding, "game-public.der")));
            out.put("endpoint", NativeClientPayload.decode(NativeConfigContainer.decode(verified(binding, "template.dll"), new byte[0]), identity).endpoint());
        } catch (GeneralSecurityException e) { throw failure(409, "实例公钥格式不兼容"); }
        out.put("clientFormatVersion", NativeClientFormat.VERSION);
        out.put("revision", revision(binding)); out.put("instanceId", instance); out.put("environment", "isolated");
        out.put("packageKind", "isolated-launcher-update-candidate"); out.put("nativeClientAcceptance", false);
        out.put("files", Arrays.asList("dof-launcher.exe", "ijl15.dll", "dof-client.dll", "manifest.json", "使用与恢复.txt"));
        return out;
    }
    synchronized void requireScriptVersion() throws IOException {
        Binding b=source();
        if(!NativeScriptContext.CLIENT_DLL_SHA256.equals(b.sha256.get("ijl15.dll")))throw failure(409,"Script 编码器与配套客户端版本不匹配");
        verified(b,"ijl15.dll");
    }
    public synchronized Job submit(Request request, long actor) throws IOException {
        if (request == null || !validId(request.requestId) || request.revision == null || !request.revision.matches("[0-9a-f]{64}")
            || request.endpoint == null || request.endpoint.length() > 253 || actor <= 0) throw failure(400, "客户端任务参数无效");
        byte[] background = null;
        if (request.backgroundBase64 != null) {
            if (request.backgroundBase64.length() > 2796204) throw failure(400, "背景图片不能超过 2 MiB");
            try { background = Base64.getDecoder().decode(request.backgroundBase64); }
            catch (IllegalArgumentException e) { throw failure(400, "背景图片编码无效"); }
            NativeClientPublication.validateBackground(background);
        }
        String backgroundHash = background == null ? null : NativeUiState.sha256(background);
        Path path = jobs.resolve(request.requestId);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Job old = read(request.requestId);
            if (old.clientFormatVersion != NativeClientFormat.VERSION)
                throw failure(409, "此客户端任务使用旧配置格式，请创建新任务");
            if (old.actor != actor || !old.revision.equals(request.revision) || !old.endpoint.equals(request.endpoint)
                || !Objects.equals(old.backgroundSha256, backgroundHash)
                || !Objects.equals(old.scriptTaskId,request.scriptTaskId)) throw failure(409, "操作编号已经用于其他客户端任务");
            return old;
        }
        Binding binding = source();
        if (!revision(binding).equals(request.revision)) throw failure(409, "发布来源版本已改变，请重新读取");
        // Avoid unbounded queued requests and memory pressure on the game host.
        List<Job> previous = list();
        if (previous.size() >= 1000) throw failure(409, "客户端任务记录已达到上限，请先按保留策略归档任务目录");
        for (Job job : previous) if (Arrays.asList("QUEUED", "RUNNING").contains(job.state)) throw failure(409, "已有客户端任务正在执行，请等待完成");
        NativeScriptJobs.Artifact script=null;
        if(request.scriptTaskId!=null){
            if(!validId(request.scriptTaskId)||scriptResolver==null)throw failure(409,"Script 配套任务尚未启用或编号无效");
            requireScriptVersion();script=scriptResolver.resolve(request.scriptTaskId,request.revision);
        }
        requireSpace(BUILD_PEAK+(script==null?0:script.job.bytes+1024*1024));
        Job job = new Job(); job.requestId = request.requestId; job.instanceId = instance; job.actor = actor;
        job.clientFormatVersion = NativeClientFormat.VERSION;
        if(script!=null){job.scriptTaskId=script.job.requestId;job.scriptSha256=script.job.sha256;job.scriptBytes=script.job.bytes;job.packageKind="isolated-dof-pvf-bundle";}
        job.backgroundSha256 = backgroundHash;
        job.revision = request.revision; job.endpoint = request.endpoint; job.state = "QUEUED"; job.createdAt = Instant.now().toString();
        Path preparing = Files.createTempDirectory(jobs, ".prepare-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
            if (background != null) {
                try (FileChannel channel = FileChannel.open(preparing.resolve("background.img"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(background); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
                }
            }
            writeAt(preparing, job);
            Files.move(preparing, path, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // A crash may leave an ignored .prepare directory, never a visible incomplete job.
            if (Files.exists(preparing, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(preparing.resolve("background.img")); Files.deleteIfExists(preparing.resolve("job.json")); Files.deleteIfExists(preparing);
            }
        }
        try { worker.submit(() -> build(job.requestId)); }
        catch (RejectedExecutionException e) { job.state = "INTERRUPTED"; job.error = "发布服务正在关闭，任务未执行"; write(job); }
        return read(job.requestId);
    }
    public synchronized List<Job> list() throws IOException {
        List<Job> out = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(jobs)) {
            for (Path path : paths) if (validId(path.getFileName().toString())) out.add(read(path.getFileName().toString()));
        }
        out.sort(Comparator.comparing((Job j) -> j.createdAt).reversed());
        return out;
    }
    public synchronized Job read(String id) throws IOException {
        if (!validId(id)) throw failure(400, "客户端任务编号无效");
        Path path = jobs.resolve(id);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw failure(404, "客户端任务不存在");
        Job job = JSON.readValue(bounded(path.resolve("job.json"), 32768), Job.class);
        if (!instance.equals(job.instanceId) || !id.equals(job.requestId)) throw failure(409, "客户端任务记录与实例不符");
        return job;
    }
    private synchronized void write(Job job) throws IOException {
        writeAt(jobs.resolve(job.requestId), job);
    }
    private static void writeAt(Path directory, Job job) throws IOException {
        Path temp = Files.createTempFile(directory, "job-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            byte[] bytes = JSON.writeValueAsBytes(job);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
            Files.move(temp, directory.resolve("job.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        } finally { Files.deleteIfExists(temp); }
    }
    private void build(String id) {
        Job job = null;
        try {
            job = read(id); job.state = "RUNNING"; job.progress = 5; write(job);
            Binding binding = source(); if (!revision(binding).equals(job.revision)) throw failure(409, "任务执行前发布来源版本已改变");
            NativeScriptJobs.Artifact script=null;
            if(job.scriptTaskId!=null){
                requireScriptVersion();if(scriptResolver==null)throw failure(409,"Script 任务解析器不可用");
                script=scriptResolver.resolve(job.scriptTaskId,job.revision);
                if(!script.job.sha256.equals(job.scriptSha256)||script.job.bytes!=job.scriptBytes)throw failure(409,"Script 配套产物已改变");
            }
            long peak=BUILD_PEAK+(script==null?0:script.job.bytes+1024*1024);requireSpace(peak);
            PublicKey identity = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(verified(binding, "game-public.der")));
            byte[] template = verified(binding, "template.dll");
            // Reject unsafe original records before changing anything, including copied credentials.
            NativeClientPublication.prepare(template, identity);
            NativeConfigContainer.Document doc = NativeClientPayload.withEndpoint(NativeConfigContainer.decode(template, new byte[0]), identity, job.endpoint);
            if (job.backgroundSha256 != null) {
                byte[] background = bounded(jobs.resolve(id).resolve("background.img"), 2 * 1024 * 1024);
                if (!NativeUiState.sha256(background).equals(job.backgroundSha256)) throw failure(409, "背景图片在任务执行前发生变化");
                doc = NativeClientPublication.withBackground(doc, background);
            }
            byte[] config = NativeClientPublication.prepare(NativeConfigContainer.encode(doc, new byte[0]), identity).bytesForValidation();
            requireSpace(peak);
            job.progress = 35; write(job);
            Path part = jobs.resolve(id).resolve("candidate.zip.part");
            Map<String,Object> manifest = new LinkedHashMap<>(); manifest.put("clientFormatVersion", NativeClientFormat.VERSION); manifest.put("instanceId", instance); manifest.put("environment", "isolated");
            manifest.put("adapterVersion", DofAdminService.VERSION); manifest.put("requestId", id); manifest.put("sourceRevision", job.revision);
            manifest.put("packageKind", job.packageKind); manifest.put("nativeClientAcceptance", false);
            if(script!=null){manifest.put("scriptTaskId",job.scriptTaskId);manifest.put("scriptSourceSha256",script.job.sourceSha256);manifest.put("nativeChecksum",script.job.nativeChecksum);manifest.put("applicationState","NOT_APPLIED");}
            Map<String,String> hashes = new LinkedHashMap<>(); manifest.put("sha256", hashes);
            Map<String,Long> sizes = new LinkedHashMap<>(); manifest.put("bytes", sizes);
            try (OutputStream file = Files.newOutputStream(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); ZipOutputStream zip = new ZipOutputStream(file)) {
                for (String name : Arrays.asList("dof-launcher.exe", "ijl15.dll")) {
                    copyVerified(zip, inputs.resolve(name), name, binding.sha256.get(name)); hashes.put(name, binding.sha256.get(name));
                    sizes.put(name, Files.size(inputs.resolve(name)));
                }
                put(zip, "dof-client.dll", config); hashes.put("dof-client.dll", NativeUiState.sha256(config));
                sizes.put("dof-client.dll", (long)config.length);
                if(script!=null){copyVerified(zip,script.path,"Script.pvf",job.scriptSha256,job.scriptBytes);hashes.put("Script.pvf",job.scriptSha256);}
                if(script!=null)sizes.put("Script.pvf",job.scriptBytes);
                String instructions=script==null ? ("仅供当前隔离客户端测试；不是完整游戏客户端，也不包含自动更新功能。\r\n"
                    + "关闭游戏及登录器后，备份独立客户端中的同名三个文件，再解压到该独立目录。不要覆盖正式客户端。\r\n"
                    + "失败时关闭客户端，恢复刚才备份的三个文件。此包不含服务端私钥、数据库连接信息、PVF或玩家数据。\r\n"
                    + "运行效果尚未统一验收。请保留 manifest.json 与本次任务编号用于核对。\r\n")
                    : ("当前测试实例的 dof-easy 登录器与不压缩加密 PVF 配套包；实际游戏消费尚待验证。\r\n"
                    + "必须先核对并应用此批次对应的隔离服参数，未应用时请勿启动。此包不会自动修改服务器。\r\n"
                    + "完全关闭游戏及登录器，备份独立客户端的dof-launcher.exe、ijl15.dll、dof-client.dll、Script.pvf，再解压本包。\r\n"
                    + "不能混用其他批次的 PVF 或配置，不覆盖正式客户端。失败时恢复这四个文件及本次隔离参数，不回滚玩家数据库。\r\n"
                    + "使用 dof-launcher.exe 登录。保留 manifest.json 核对批次和 SHA-256。此包没有数据库凭据或服务端身份私钥。\r\n");
                byte[] guide=instructions.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                put(zip, "使用与恢复.txt",guide); hashes.put("使用与恢复.txt",NativeUiState.sha256(guide));sizes.put("使用与恢复.txt",(long)guide.length);
                put(zip, "manifest.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            }
            job.progress = 90; write(job);
            try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) { channel.force(true); }
            job.bytes = Files.size(part); job.sha256 = hashFile(part);
            // Verify the complete archive before its name becomes downloadable.
            verifyArchive(part, hashes);
            // The archive already occupies disk; only metadata remains to write.
            requireSpace(1024 * 1024);
            Files.move(part, jobs.resolve(id).resolve("candidate.zip"), StandardCopyOption.ATOMIC_MOVE);
            job.state = "READY_FOR_TEST"; job.progress = 100; job.finishedAt = Instant.now().toString(); write(job);
        } catch (Exception e) {
            if (job != null) {
                job.state = "FAILED"; job.error = e instanceof DofFailure || e instanceof IllegalArgumentException
                    ? e.getMessage() : "客户端包生成失败；未替换旧文件，请核对输入、磁盘空间和任务目录";
                job.finishedAt = Instant.now().toString();
                try { write(job); } catch (IOException ignored) { /* An unchanged RUNNING record becomes INTERRUPTED on recovery. */ }
            }
        }
    }
    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b & 255)); return out.toString();
    }
    private static void copyVerified(ZipOutputStream zip, Path source, String name, String expected) throws IOException {
        copyVerified(zip,source,name,expected,MAX_INPUT);
    }
    private static void copyVerified(ZipOutputStream zip, Path source, String name, String expected,long limit) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > limit) throw failure(409, "发布输入缺失、超限或不是普通文件");
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
        MessageDigest digest = digest(); long total = 0;
        try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("客户端包生成中断");
                if ((total += n) > limit) throw failure(409, "发布输入超过限制");
                digest.update(buffer, 0, n); zip.write(buffer, 0, n);
            }
        }
        zip.closeEntry();
        if (!hex(digest.digest()).equals(expected)) throw failure(409, "发布输入发生变化，请重新准备并核对来源");
    }
    private static String hashFile(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n); }
            StringBuilder out = new StringBuilder(); for (byte b : digest.digest()) out.append(String.format("%02x", b & 255)); return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void verifyArchive(Path path, Map<String,String> expected) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                long limit="Script.pvf".equals(entry.getName())&&expected.containsKey("Script.pvf")?Integer.MAX_VALUE:MAX_INPUT;
                if (!names.add(entry.getName()) || entry.getSize() < 0 || entry.getSize() > limit) throw new IOException("Invalid archive entry");
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[65536]; int n; long total = 0; MessageDigest digest = digest();
                    while ((n = in.read(buffer)) != -1) { if ((total += n) > limit) throw new IOException("Archive entry exceeds bound"); digest.update(buffer, 0, n); }
                    if (expected.containsKey(entry.getName()) && !expected.get(entry.getName()).equals(hex(digest.digest()))) throw new IOException("Archive checksum mismatch");
                }
            }
        }
        Set<String> allowed = new HashSet<>(expected.keySet()); allowed.add("manifest.json"); allowed.add("使用与恢复.txt");
        if (!names.equals(allowed)) throw new IOException("Unexpected archive content");
    }
    public synchronized Path download(String id) throws IOException {
        Job job = read(id);
        if (!"READY_FOR_TEST".equals(job.state)) throw failure(409, "此任务没有可下载的完整测试包");
        if (job.clientFormatVersion != NativeClientFormat.VERSION || !job.revision.equals(revision(source())))
            throw failure(409, "客户端任务格式或来源版本不匹配，请重新生成");
        Path path = jobs.resolve(id).resolve("candidate.zip");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != job.bytes || !hashFile(path).equals(job.sha256)) throw failure(409, "客户端包损坏或缺失，停止下载");
        return path;
    }
    @Override public void close() throws IOException {
        worker.shutdown();
        try { if (!worker.awaitTermination(30, TimeUnit.SECONDS)) throw new IOException("Publication worker still active; lock retained"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Publication shutdown interrupted", e); }
        lock.release(); lockChannel.close();
    }
}
