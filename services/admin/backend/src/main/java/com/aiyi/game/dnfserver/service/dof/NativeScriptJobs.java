package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Private, durable PVF workspace. No database writes or installation side effects.
 * HTTP DTOs never contain the content parameter; it stays in a separate private file.
 */
public final class NativeScriptJobs implements AutoCloseable {
    static final long RESERVE=2L*1024*1024*1024, OVERHEAD=192L*1024*1024;
    private static final ObjectMapper JSON=new ObjectMapper();
    interface SpaceProbe {long usable(Path path)throws IOException;}
    private final Path root,uploads,tasks;
    private final String instance;
    private final SpaceProbe space;
    private final SecureRandom random=new SecureRandom();
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ExecutorService worker;
    private boolean closed;

    public static final class Upload {
        public String uploadId,instanceId,revision,state,createdAt,sha256,error;
        public long actor,bytes,indexBytes,fileCount;
    }
    public static final class Request {public String requestId,uploadId,revision;}
    public static final class Job {
        public String requestId,uploadId,instanceId,revision,state,createdAt,finishedAt,error;
        public String sourceSha256,sha256,nativeChecksum,parameterSha256;
        public long actor,sourceBytes,bytes;
        public int progress;
        public boolean compressed=false,nativeClientAcceptance=false;
        public String applicationState="NOT_APPLIED";
    }
    public static final class Artifact {
        public final Path path;
        public final Job job;
        private Artifact(Path path,Job job){this.path=path;this.job=job;}
    }
    public NativeScriptJobs(Path root,String instance)throws IOException {this(root,instance,p->Files.getFileStore(p).getUsableSpace());}
    NativeScriptJobs(Path path,String instance,SpaceProbe space)throws IOException {
        this.instance=Objects.requireNonNull(instance);this.space=Objects.requireNonNull(space);
        root=path.toAbsolutePath().normalize();
        if(!root.equals(root.toRealPath()))throw fail(409,"Script 工作目录不能通过链接访问");
        uploads=directory(root.resolve("uploads"));tasks=directory(root.resolve("tasks"));
        lockChannel=FileChannel.open(root.resolve("worker.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        FileLock acquired;
        try{acquired=lockChannel.tryLock();}catch(OverlappingFileLockException e){lockChannel.close();throw fail(409,"Script 工作目录已被使用");}
        if(acquired==null){lockChannel.close();throw fail(409,"Script 工作目录已被使用");}lock=acquired;
        try {
            for(Path dir:children(uploads)) {
                Upload u=readUpload(dir.getFileName().toString());
                if("UPLOADING".equals(u.state)) {u.state="INTERRUPTED";u.error="上传中断，请核对后使用新编号上传";save(dir,u);}
            }
            for(Job j:list())if(Arrays.asList("QUEUED","RUNNING").contains(j.state)) {
                j.state="INTERRUPTED";j.error="生成中断，未应用任何服务器参数";j.finishedAt=now();save(tasks.resolve(j.requestId),j);
            }
        }catch(IOException|RuntimeException e){lock.release();lockChannel.close();throw e;}
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"qingkong-script-build");t.setDaemon(true);return t;});
    }
    private static DofFailure fail(int status,String msg){return new DofFailure(status,msg);}
    private static String now(){return Instant.now().toString();}
    private static void id(String id){if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw fail(400,"Script 编号无效");}
    private static void revision(String r){if(r==null||!r.matches("[0-9a-f]{64}"))throw fail(400,"Script 来源版本无效");}
    static Path directory(Path p)throws IOException {
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(p,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw fail(409,"Script 工作目录结构异常");return p;
    }
    private static List<Path> children(Path root)throws IOException {
        List<Path> out=new ArrayList<>();try(DirectoryStream<Path>s=Files.newDirectoryStream(root)) {
            for(Path p:s)if(!p.getFileName().toString().startsWith(".")){id(p.getFileName().toString());if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw fail(409,"Script 任务目录异常");out.add(p);}
        }return out;
    }
    private void requireSpace(long peak)throws IOException {if(space.usable(root)<RESERVE+OVERHEAD+peak)throw fail(507,"Script 空间不足，需要 2 GiB 基础余量、并行任务余量和本次文件峰值");}
    private static <T>T load(Path directory,Class<T> type)throws IOException {
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw fail(404,"Script 记录不存在");
        Path p=directory.resolve("record.json");
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>32768)throw fail(409,"Script 记录异常");
        try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){return JSON.readValue(in,type);}
    }
    private static void save(Path dir,Object value)throws IOException {
        Path temp=Files.createTempFile(dir,".record-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            byte[] bytes=JSON.writeValueAsBytes(value);
            try(FileChannel f=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())f.write(b);f.force(true);}
            Files.move(temp,dir.resolve("record.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);sync(dir);
        }finally{Files.deleteIfExists(temp);}
    }
    private static void sync(Path p)throws IOException {try(FileChannel f=FileChannel.open(p,StandardOpenOption.READ)){f.force(true);}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x&255));return s.toString();}
    static String hash(Path p)throws IOException {
        MessageDigest md=digest();try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){active();md.update(b,0,n);}}return hex(md.digest());
    }
    private static void active()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Script 任务已中断");}
    private void live(){if(closed)throw fail(503,"Script 服务正在关闭");}

    public synchronized Upload upload(String id,String revision,long actor,long length,InputStream input)throws IOException {
        live();id(id);revision(revision);if(actor<=0||length<80||length>NativePvfSource.MAX_BYTES)throw fail(400,"PVF 上传长度无效");
        Path dir=uploads.resolve(id);
        if(Files.exists(dir,LinkOption.NOFOLLOW_LINKS))throw fail(409,"上传编号已存在，请先查询记录；不得覆盖或复用编号");
        if(children(uploads).size()>=1000)throw fail(409,"Script 上传记录已达上限");
        requireSpace(length);directory(dir);
        Upload u=new Upload();u.uploadId=id;u.instanceId=instance;u.revision=revision;u.actor=actor;u.bytes=length;u.state="UPLOADING";u.createdAt=now();save(dir,u);
        Path part=dir.resolve("source.pvf.part");
        try {
            MessageDigest md=digest();long copied=0,checked=0;
            try(OutputStream out=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                byte[] b=new byte[65536];
                while(copied<length){active();int n=input.read(b,0,(int)Math.min(b.length,length-copied));if(n<0)throw new EOFException("PVF 上传被截断");if(n==0)continue;out.write(b,0,n);md.update(b,0,n);copied+=n;
                    if(copied-checked>=16*1024*1024){requireSpace(length-copied);checked=copied;}}
                if(input.read()!=-1)throw new IOException("PVF 上传超过声明长度");
            }
            NativePvfSource.Header header;
            try(InputStream in=Files.newInputStream(part)){header=NativePvfSource.validate(in,length);}
            u.indexBytes=header.indexBytes;u.fileCount=header.fileCount;u.sha256=hex(md.digest());sync(part);requireSpace(1024*1024);
            Files.move(part,dir.resolve("source.pvf"),StandardCopyOption.ATOMIC_MOVE);u.state="READY";save(dir,u);return u;
        }catch(IOException|RuntimeException e){u.state=e instanceof InterruptedIOException?"INTERRUPTED":"FAILED";u.error=safeError(e);save(dir,u);throw e;}
        finally{Files.deleteIfExists(part);}
    }
    public synchronized Upload readUpload(String id)throws IOException {
        id(id);Upload u=load(uploads.resolve(id),Upload.class);
        if(!id.equals(u.uploadId)||!instance.equals(u.instanceId))throw fail(409,"上传记录不属于当前实例");return u;
    }
    public synchronized List<Upload> uploads()throws IOException {List<Upload> out=new ArrayList<>();for(Path p:children(uploads))out.add(readUpload(p.getFileName().toString()));out.sort(Comparator.comparing((Upload u)->u.createdAt).reversed());return out;}
    public synchronized Job submit(Request r,long actor)throws IOException {
        live();if(r==null||actor<=0)throw fail(400,"Script 任务参数无效");id(r.requestId);id(r.uploadId);revision(r.revision);
        Path dir=tasks.resolve(r.requestId);
        if(Files.exists(dir,LinkOption.NOFOLLOW_LINKS)) {
            Job old=read(r.requestId);if(old.actor!=actor||!old.uploadId.equals(r.uploadId)||!old.revision.equals(r.revision))throw fail(409,"Script 请求编号已用于不同操作");return old;
        }
        Upload u=readUpload(r.uploadId);
        if(!"READY".equals(u.state)||!u.revision.equals(r.revision))throw fail(409,"上传未完成或来源版本不同");
        List<Job> previous=list();if(previous.size()>=1000)throw fail(409,"Script 任务记录已达上限");
        for(Job old:previous)if(Arrays.asList("QUEUED","RUNNING").contains(old.state))throw fail(409,"已有 Script 任务正在生成");
        requireSpace(u.bytes+1280);Job j=new Job();j.requestId=r.requestId;j.uploadId=r.uploadId;j.revision=r.revision;j.actor=actor;j.instanceId=instance;
        j.sourceSha256=u.sha256;j.sourceBytes=u.bytes;j.state="QUEUED";j.createdAt=now();
        Path temp=Files.createTempDirectory(tasks,".prepare-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        save(temp,j);Files.move(temp,dir,StandardCopyOption.ATOMIC_MOVE);sync(tasks);
        worker.submit(()->build(j.requestId));return read(j.requestId);
    }
    private void build(String id) {
        Job j=null;Path part=tasks.resolve(id).resolve("Script.pvf.part");
        try {
            j=read(id);j.state="RUNNING";j.progress=5;write(j);requireSpace(j.sourceBytes+1280);
            Path source=uploads.resolve(j.uploadId).resolve("source.pvf");
            if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)!=j.sourceBytes)throw fail(409,"PVF 来源文件缺失或长度改变");
            NativePvfSource.Header h;try(InputStream in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)){h=NativePvfSource.validate(in,j.sourceBytes);}
            NativeScriptParameters parameters=NativeScriptParameters.create(random);
            Path secret=tasks.resolve(id).resolve("parameters.private");
            Files.write(secret,parameters.envelope(NativeScriptContext.CLIENT_DLL_SHA256).getBytes(java.nio.charset.StandardCharsets.US_ASCII),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-------"));sync(secret);j.parameterSha256=hash(secret);
            j.progress=20;write(j);final long expected=j.sourceBytes+1280;final long[] written={0},checked={0};
            NativeScriptFileCodec.Result result;
            try(InputStream in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);OutputStream file=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                OutputStream out=new FilterOutputStream(file){
                    @Override public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
                    @Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);written[0]+=len;if(written[0]-checked[0]>=16*1024*1024){requireSpace(Math.max(0,expected-written[0]));checked[0]=written[0];}}
                };
                result=NativeScriptFileCodec.encode(in,out,j.sourceBytes,j.sourceSha256,parameters.effectiveMask(h.indexCrc),false,NativeScriptFileCodec.newTrailer(random));
            }
            sync(part);j.progress=90;write(j);
            if(!hash(part).equals(result.artifactSha256))throw fail(409,"Script 产物回读校验失败");
            requireSpace(1024*1024);Files.move(part,tasks.resolve(id).resolve("Script.pvf"),StandardCopyOption.ATOMIC_MOVE);
            j.sha256=result.artifactSha256;j.nativeChecksum=result.nativeChecksum;j.bytes=result.artifactBytes;
            j.state="READY_FOR_TEST";j.progress=100;j.finishedAt=now();write(j);
        }catch(Exception e){if(j!=null){j.state=e instanceof InterruptedIOException?"INTERRUPTED":"FAILED";j.error=safeError(e);j.finishedAt=now();try{write(j);}catch(IOException ignored){/* recovery converts RUNNING to INTERRUPTED */}}}
        finally{try{Files.deleteIfExists(part);}catch(IOException ignored){/* never downloadable */}}
    }
    private static String safeError(Exception e){return e instanceof DofFailure||e instanceof IllegalArgumentException?e.getMessage():"Script 文件处理失败，请核对文件完整性、磁盘空间和任务状态；服务器参数未改动";}
    private synchronized void write(Job j)throws IOException{save(tasks.resolve(j.requestId),j);}
    public synchronized Job read(String id)throws IOException{id(id);Job j=load(tasks.resolve(id),Job.class);if(!id.equals(j.requestId)||!instance.equals(j.instanceId))throw fail(409,"Script 任务不属于当前实例");return j;}
    public synchronized List<Job> list()throws IOException{List<Job> out=new ArrayList<>();for(Path p:children(tasks))out.add(read(p.getFileName().toString()));out.sort(Comparator.comparing((Job j)->j.createdAt).reversed());return out;}
    public synchronized Artifact artifact(String id,String revision)throws IOException {
        Job j=read(id);if(!"READY_FOR_TEST".equals(j.state)||!j.revision.equals(revision))throw fail(409,"Script 任务未就绪或来源版本已改变");
        Path p=tasks.resolve(id).resolve("Script.pvf");
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)!=j.bytes||!hash(p).equals(j.sha256))throw fail(409,"Script 产物缺失或损坏");
        parameters(id);return new Artifact(p,j);
    }
    // Internal application service only. Never return this value through HTTP.
    synchronized String parameters(String id)throws IOException {
        id(id);Path p=tasks.resolve(id).resolve("parameters.private");
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>128)throw fail(409,"Script 私有参数缺失或异常");
        if(!Objects.equals(read(id).parameterSha256,hash(p)))throw fail(409,"Script 私有参数与任务不匹配");
        String value=new String(Files.readAllBytes(p),java.nio.charset.StandardCharsets.US_ASCII);
        NativeScriptParameters.decode(value,NativeScriptContext.CLIENT_DLL_SHA256);return value;
    }
    @Override public void close()throws IOException {
        synchronized(this){closed=true;worker.shutdown();}
        try{if(!worker.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Script worker still active; lock retained");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Script shutdown interrupted",e);}
        lock.release();lockChannel.close();
    }
}
