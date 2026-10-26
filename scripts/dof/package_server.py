"""Package the fixed server runtime and explicit validation status, locally only."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'services/game/runtime/ubuntu'))
from gateway import assets


def package(runtime,validation,output):
    runtime=assets.verify(runtime);output=Path(output).absolute()
    report=json.loads(Path(validation).read_text())
    locked=json.loads(assets.LOCK.read_text())
    if report.get('runtimeManifest')!=locked:
        raise ValueError('Validation does not bind the current runtime manifest')
    if output.exists() or output.is_symlink():raise ValueError('Package output must be new')
    files={row['path']:(runtime/row['path']).read_bytes() for row in assets.manifest()}
    files['manifest.json']=(runtime/'manifest.json').read_bytes()
    for name,default in assets.templates():files[assets.template_path(name).as_posix()]=default.read_bytes()
    files['validation.json']=(json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode()
    files['NOTICES.txt']=(
        '本包由用户提供的固定哈希晴空免授权材料派生，保留第三方来源与作者归属。\n'
        '原材料标注：请勿商业用途，仅单机娱乐学习使用。重命名不授予额外许可，也不将第三方程序变为项目自研代码。\n'
        '本包不含用户私钥、数据库密码、客户端、管理后台或原授权版工具。\n').encode()
    files['README.txt']=(
        'dof-easy server runtime assets\n'
        'Use this directory with the matching source installer --dof-assets option and matching rebuilt game image.\n'
        'This is an asset bundle, not a prebuilt Docker image. Read validation.json before use.\n'
        'releaseReady='+str(bool(report.get('releaseReady'))).lower()+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.dof-package-',dir=output.parent) as temporary:
        archive=Path(temporary)/'server.tar.gz'
        with archive.open('wb') as sink,gzip.GzipFile(filename='',mode='wb',fileobj=sink,mtime=0) as zipped:
            with tarfile.open(fileobj=zipped,mode='w',format=tarfile.PAX_FORMAT) as tar:
                for name,data in sorted(files.items()):
                    info=tarfile.TarInfo('dof-server-runtime/'+name);info.size=len(data)
                    info.mode=0o700 if name.startswith('bin/') else 0o600
                    tar.addfile(info,io.BytesIO(data))
        with archive.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
        if output.exists() or output.is_symlink():raise ValueError('Package output appeared')
        archive.rename(output)
    return dict(sha256=digest,bytes=output.stat().st_size,files=len(files),releaseReady=bool(report.get('releaseReady')))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',type=Path,required=True);p.add_argument('--validation',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(json.dumps(package(a.runtime,a.validation,a.output),sort_keys=True))
