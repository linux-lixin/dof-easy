#!/usr/bin/env python3
"""Offline, Git-scoped documentation contracts (Python 3.11+, stdlib only)."""
import argparse
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import posixpath
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

TEXT_EXTENSIONS={'.md','.txt','.rst','.adoc'}
SECTIONS=('getting-started','operations','reference','architecture','development','status','validation','history')
BASELINE='ecd4505d1ebdd28486b13a718cd991874efbe96f'
STATUS_POINTERS=dict(zip(
    ('applicationSourceCommit','adminImage','adminTag','adminJarSha256','gameImage','gameTag','gameHealthy','gameFreshSourceBuildClaimed','managementBackupCount','backupScope','databaseRestoreVerified','applicationRecoveryVerified','oldImageRollbackAvailable','automaticRetentionPolicyChanged','standardMavenPackageCompleted','freshSourceMavenCompileCompleted','releaseReady','completeReplacement','gameplayAcceptancePerformed'),
    ('/deployment/sourceCommit','/deployment/adminImage','/deployment/adminTag','/deployment/adminAppSha256','/gameRecreation/gameImage','/gameImageRecovery/tag','/gameRecreation/gameHealthy','/gameRecreation/gameFreshSourceBuildClaimed','/retention/currentManagementBackupCount','/retention/backupScope','/backupRestore/databaseRestoreVerified','/backupRestore/applicationRecoveryVerified','/retention/oldImageRollbackAvailable','/retention/automaticRetentionPolicyChanged','/build/standardMavenPackageCompleted','/build/freshSourceMavenCompileCompleted','/releaseReady','/completeReplacement','/gameplayAcceptancePerformed')))

def tracked_files(root, includes=()):
    result=subprocess.run(['git','-C',str(root),'ls-files','-z','--cached'],check=True,capture_output=True)
    files=set(result.stdout.decode('utf8').rstrip('\0').split('\0'))- {''}
    for p in includes:
        path=Path(root,p).resolve()
        try: relative=path.relative_to(Path(root).resolve()).as_posix()
        except ValueError: raise ValueError('--include must remain inside repository')
        if path.is_dir(): raise ValueError('--include must name an explicit file')
        if any(x.startswith('.') for x in Path(relative).parts): raise ValueError('Private include prohibited')
        files.add(relative)
    return files

def without_code(text):
    # Inline code and fenced examples are not Markdown navigation.
    text=re.sub(r'(?ms)^\s*(`{3,}|~{3,})[^\n]*\n.*?^\s*\1\s*$','',text)
    return re.sub(r'`[^`\n]+`','',text)

def anchors(text):
    seen={}; result=set(re.findall(r'(?:id|name)=["\']([^"\']+)',text))
    text=re.sub(r'(?ms)^\s*(`{3,}|~{3,})[^\n]*\n.*?^\s*\1\s*$','',text)
    lines=text.splitlines(); headings=[]
    for i,line in enumerate(lines):
        match=re.match(r'^ {0,3}#{1,6}\s+(.+?)(?:\s+#+)?$',line)
        if match: headings.append(match[1])
        elif line.strip() and i+1<len(lines) and re.fullmatch(r' {0,3}(?:=+|-+)\s*',lines[i+1]): headings.append(line)
    for heading in headings:
        heading=re.sub(r'!?\[([^\]]*)\]\([^)]*\)',r'\1',heading)
        heading=re.sub(r'<[^>]*>','',heading).strip().lower()
        slug=re.sub(r'[^\w\-\s]','',heading,flags=re.UNICODE).replace('_','_')
        slug=re.sub(r'\s','-',slug)
        suffix=seen.get(slug,0); seen[slug]=suffix+1
        result.add(slug+('-'+str(suffix) if suffix else ''))
    return result

def destinations(text):
    text=without_code(text)
    definitions={m[1].strip().casefold():m[2] for m in re.finditer(r'(?m)^\s*\[([^\]]+)\]:\s*(<[^>]+>|\S+)',text)}
    targets=[]
    for m in re.finditer(r'!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+["\'][^\n]*?["\'])?\s*\)',text): targets.append(m[1])
    for m in re.finditer(r'!?\[([^\]\n]+)\]\[([^\]\n]*)\]',text):
        label=(m[2] or m[1]).strip().casefold()
        targets.append(definitions.get(label,'!UNDEFINED:'+label))
    # Shortcut reference links, including images.
    for m in re.finditer(r'!?\[([^\]\n]+)\](?![\[(\]:])',text):
        if m[1].strip().casefold() in definitions: targets.append(definitions[m[1].strip().casefold()])
    targets.extend(re.findall(r'<(?:img|a)\b[^>]*?\b(?:src|href)=["\']([^"\']+)["\']',text,re.I))
    return targets

def check_links(root, files):
    root=Path(root); errors=[]; graph={}; anchor_cache={}
    for p in sorted(files):
        if Path(p).suffix.lower() not in TEXT_EXTENSIONS: continue
        file=root/p
        if not file.is_file():
            errors.append(f'missing tracked document: {p}'); continue
        try: text=file.read_text(encoding='utf-8-sig')
        except UnicodeError:
            errors.append(f'non UTF-8 document: {p}'); continue
        graph[p]=set()
        for raw in destinations(text):
            raw=raw.strip('<>')
            if raw.startswith('!UNDEFINED:'):
                errors.append(f'{p}: undefined reference {raw}'); continue
            url=urlsplit(raw)
            if url.scheme or raw.startswith('//'): continue
            path=unquote(url.path)
            target=posixpath.normpath(posixpath.join(posixpath.dirname(p),path)) if path else p
            if path.startswith('/'): target=path.lstrip('/')
            if target.startswith('../'):
                errors.append(f'{p}: link escapes repository: {raw}'); continue
            is_dir=any(f.startswith(target.rstrip('/')+'/') for f in files)
            if target not in files and not is_dir:
                errors.append(f'{p}: missing/untracked link or image: {raw}'); continue
            if is_dir and target not in files:
                target=target.rstrip('/')+'/README.md'
                if target in files: graph[p].add(target)
            else: graph[p].add(target)
            if url.fragment and Path(target).suffix.lower() in TEXT_EXTENSIONS:
                if target not in anchor_cache: anchor_cache[target]=anchors((root/target).read_text(encoding='utf-8-sig'))
                if unquote(url.fragment) not in anchor_cache[target]: errors.append(f'{p}: missing anchor: {raw}')
    return errors,graph

def check_navigation(pages,graph):
    visited=set(); queue=['README.md','docs/README.md']
    while queue:
        p=queue.pop()
        if p in visited: continue
        visited.add(p); queue.extend(graph.get(p,()))
    return [f'unreachable current page: {p["path"]}' for p in pages if p['kind']=='current' and p['path'] not in visited]

def check_redirects(pages,graph):
    errors=[]; table={p['path']:p for p in pages}
    for p in pages:
        if p['kind']!='redirect': continue
        authority=p.get('authority')
        if authority not in table or table[authority]['kind']=='redirect': errors.append(f'redirect chain/cycle or absent authority: {p["path"]}')
        if authority not in graph.get(p['path'],()): errors.append(f'redirect does not link authority: {p["path"]}')
        if graph.get(p['path'],set())!={authority}: errors.append(f'redirect must have one direct target: {p["path"]}')
    return errors

def check_immutable(root,manifest,files):
    errors=[]
    for p,expected in manifest.items():
        if p not in files or not (Path(root)/p).is_file(): errors.append(f'immutable evidence missing: {p}')
        else:
            data=(Path(root)/p).read_bytes()
            # Git auto-text changes these two upstream text files to CRLF on Windows.
            # JSON, images, placeholders and all other frozen objects remain byte-strict.
            if p in ('services/game/LICENSE','services/game/THIRD_PARTY_NOTICES.txt'): data=data.replace(b'\r\n',b'\n')
            if hashlib.sha256(data).hexdigest()!=expected: errors.append(f'immutable bytes changed: {p}')
    return errors

def pointer_value(obj,pointer):
    for k in pointer.strip('/').split('/'):
        k=k.replace('~1','/').replace('~0','~')
        obj=obj[int(k)] if isinstance(obj,list) else obj[k]
    return obj

def check_status(root,current,files,strict=False):
    errors=[]; p=current.get('evidenceFile')
    if p not in files: return ['status evidence absent from tracked inventory']
    evidence=json.loads((Path(root)/p).read_text(encoding='utf8'))
    if strict:
        for key in ('asOf','timezone'):
            if current.get(key)!=evidence.get('date' if key=='asOf' else key): errors.append('status evidence date/timezone mismatch: '+key)
        required=set(STATUS_POINTERS)
        if set(current.get('facts',{}))!=required: errors.append('status required deployment facts missing or unknown')
        if current.get('documentationBaseline')!=BASELINE: errors.append('status documentation baseline changed')
    for name,fact in current.get('facts',{}).items():
        if strict and fact.get('pointer')!=STATUS_POINTERS.get(name): errors.append('status fact uses wrong evidence source: '+name)
        try:
            if fact['value']!=pointer_value(evidence,fact['pointer']): errors.append('status evidence mismatch: '+name)
        except (KeyError,ValueError,TypeError,IndexError): errors.append('invalid status pointer: '+name)
        if name=='gameImage' and fact.get('pointer')!='/gameRecreation/gameImage': errors.append('status gameImage must use final gameRecreation evidence')
        if name in ('releaseReady','completeReplacement','gameplayAcceptancePerformed') and fact.get('value') is not False: errors.append('documentation cannot promote acceptance: '+name)
    return errors

def check_port_table(root):
    root=Path(root)
    spec=importlib.util.spec_from_file_location('dof_documented_ports',root/'scripts/diagnostics/ubuntu_ports.py')
    module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    expected=[line for line in module.render(3,[11,52]).splitlines() if line.startswith('|')]
    actual=[line for line in (root/'docs/reference/ports.md').read_text(encoding='utf8').splitlines() if line.startswith('|')]
    return [] if expected==actual else ['port table differs from rendered source templates (manual group=3 channels=11,52)']

def check_ledger_references(root,ledgers,files):
    errors=[]
    for p in ledgers:
        text=(Path(root)/p).read_text(encoding='utf8')
        for reference in set(re.findall(r'(?:docs|metadata|scripts|services|tests)/[A-Za-z0-9_./-]+\.(?:json|md|py)',text)):
            if reference not in files: errors.append(f'ledger reference missing: {p}: {reference}')
    return errors

def baseline_blobs(root,paths):
    request=''.join(BASELINE+':'+p+'\n' for p in paths).encode('utf8')
    result=subprocess.run(['git','-C',str(root),'cat-file','--batch'],input=request,check=True,capture_output=True).stdout
    blobs={}; offset=0
    for p in paths:
        end=result.index(b'\n',offset); header=result[offset:end].split()
        if len(header)!=3 or header[1]!=b'blob': raise ValueError('baseline blob absent: '+p)
        size=int(header[2]); offset=end+1; blobs[p]=result[offset:offset+size]; offset+=size+1
    return blobs

def check_baseline(root,catalog,files):
    if catalog.get('baseline')!=BASELINE: return ['catalog baseline differs from approved baseline']
    names=subprocess.run(['git','-C',str(root),'ls-tree','-r','--name-only',BASELINE],capture_output=True,check=True).stdout.decode('utf8').splitlines()
    prose={p for p in names if Path(p).suffix.lower() in TEXT_EXTENSIONS and not p.startswith('services/game/assets/') and p not in ('services/game/THIRD_PARTY_NOTICES.txt','scripts/protocol/dof/requirements.txt')}
    errors=[]
    if {p['original'] for p in catalog['originals']}!=prose: errors.append('catalog originals differ from actual 86 baseline documents')
    frozen={p for p in names if p.startswith('docs/') and Path(p).suffix.lower() in {'.json','.png','.jpg','.jpeg','.gif','.svg','.webp'} or p.startswith('services/game/assets/') and Path(p).suffix.lower() in {'.md','.txt'} or Path(p).name.lower().startswith(('license','copying')) or p=='services/game/THIRD_PARTY_NOTICES.txt'}
    if set(catalog['immutable'])!=frozen: errors.append('immutable manifest coverage differs from actual baseline')
    ledgers=['services/admin/backend/src/main/resources/dof/scope.json','services/admin/backend/src/main/resources/dof/execution-plan.json']
    blobs=baseline_blobs(root,sorted(frozen|set(ledgers)))
    for p in frozen:
        if catalog['immutable'].get(p)!=hashlib.sha256(blobs[p]).hexdigest(): errors.append('immutable manifest hash differs from baseline: '+p)
    for p in ledgers:
        if p not in files or (Path(root)/p).read_bytes()!=blobs[p]: errors.append('documentation must preserve feature ledger: '+p)
    errors+=check_ledger_references(root,ledgers,files)
    return errors

def argument_defaults(text):
    result={}
    for node in ast.walk(ast.parse(text)):
        if not isinstance(node,ast.Call) or not isinstance(node.func,ast.Attribute) or node.func.attr!='add_argument': continue
        names=[a.value for a in node.args if isinstance(a,ast.Constant) and isinstance(a.value,str)]
        for keyword in node.keywords:
            if keyword.arg=='default' and isinstance(keyword.value,ast.Constant):
                for name in names: result[name]=str(keyword.value.value)
    return result

def check_default_table(root, rows=None):
    rows=rows or [('频道','--channels'),('网页端口','--admin-port'),('游戏绑定','--bind'),('插件','--plugins')]
    root=Path(root); errors=[]
    defaults=argument_defaults((root/'scripts/container/manage.py').read_text(encoding='utf8'))
    doc=(root/'docs/reference/cli.md').read_text(encoding='utf8')
    for label,flag in rows:
        match=re.search(r'^\|\s*'+re.escape(label)+r'\s*\|([^|]+)\|([^|]+)\|',doc,re.M)
        if not match or flag not in defaults or match[2].strip()!=defaults[flag]: errors.append(f'CLI default differs from source: {label} {flag}')
    return errors

def check_public_material(root, files):
    errors=[]
    for p in sorted(files):
        if p.startswith('docs/') and (re.search(r'\.private(?:\.|$)',p) or Path(p).suffix in {'.zip','.tar','.gz','.7z','.pem','.key','.pfx'}):
            errors.append('private/archive runtime material in public documents: '+p)
        if Path(p).suffix.lower() not in TEXT_EXTENSIONS|{'.json'} or not (Path(root)/p).is_file(): continue
        if not (p.startswith('docs/') or p.endswith('README.md') or p=='CONTRIBUTING.md'): continue
        if any(part.startswith('.') for part in Path(p).parts) or re.search(r'(?:\.private(?:\.|$)|(?:^|/)(?:runtime\.env|credentials\.json|deployment-profile\.json)$)',p):
            errors.append('private runtime material in public documents: '+p)
        text=(Path(root)/p).read_text(encoding='utf-8-sig')
        if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})',text): errors.append('credential/private key in public document: '+p)
        for value in re.findall(r'(?i)(?:password|passwd|ADMIN_PASSWORD|MYSQL_ROOT_PASSWORD)\s*[:=]\s*["\']([^"\'\n]{8,})["\']',text):
            if not re.search(r'(?i)(example|placeholder|change.?me|your|replace|\$|<|密码|示例|测试|占位)',value): errors.append('possible literal credential in public document: '+p)
        for target in destinations(text):
            if re.search(r'(?:\.private(?:[./#]|$)|(?:^|/)\.dof-easy/|(?:^|/)runtime\.env(?:#|$)|(?:^|/)credentials\.json)',target): errors.append('private file link in public document: '+p)
    return errors

def check_contracts(root,catalog,files,graph):
    root=Path(root); errors=[]; pages=catalog['pages']; paths=[p['path'] for p in pages]
    if len(paths)!=len(set(paths)): errors.append('duplicate catalog pages')
    originals=catalog['originals']
    if len(originals)!=86 or len({p['original'] for p in originals})!=86: errors.append('original prose coverage must be 86/86')
    frozen_json=[p for p in catalog['immutable'] if p.startswith('docs/') and p.endswith('.json')]
    if len(frozen_json)!=88: errors.append('original evidence coverage must be 88/88')
    table={p['path']:p for p in pages}
    for row in originals:
        for name in ('original','destination'):
            if row[name] not in files: errors.append('catalog target absent: '+row[name])
        if row['original']!=row['destination'] and table.get(row['original'],{}).get('authority')!=row['destination']: errors.append('migration catalog authority mismatch: '+row['original'])
        for source in row['sources']:
            if source not in files and not any(p.startswith(source.rstrip('/')+'/') for p in files): errors.append('catalog source absent: '+source)
    for p in paths:
        if p not in files: errors.append('catalog page absent: '+p)
        if p.startswith('docs/') and table[p]['kind']=='current' and p not in ('docs/README.md','docs/migration-inventory.md') and p.split('/')[1] not in SECTIONS: errors.append('current page outside category: '+p)
    for p in files:
        if p.startswith('docs/') and p.endswith('.md') and p not in table: errors.append('uncatalogued document: '+p)
    for section in SECTIONS:
        if f'docs/{section}/README.md' not in files: errors.append('missing directory README: '+section)
    errors+=check_navigation(pages,graph)+check_redirects(pages,graph)
    errors+=check_default_table(root)
    cli=(root/'docs/reference/cli.md').read_text(encoding='utf8')
    manager=(root/'scripts/container/manage.py').read_text(encoding='utf8')
    menu=(root/'scripts/container/menu.py').read_text(encoding='utf8')
    # Non-argparse defaults are bounded source contracts: fail visibly on source drift.
    source_contracts=[("getattr(args, 'launcher', None) or 'dof'",manager), ("getattr(args,'admin_bind',None) or '127.0.0.1'",manager), ("'offline' if args.offline else 'build' if args.build else 'pull'",manager), ("'--launcher','native'",menu), ("image_choice = choose('镜像：1 本机源码构建 / 2 自己的仓库拉取', ('1','2'), '1')",menu), ("admin_bind = '0.0.0.0' if choose",menu)]
    for expression,source in source_contracts:
        if expression not in source: errors.append('CLI non-argparse source contract changed: '+expression)
    for label,a,b in [('登录模式','native','dof'),('镜像来源','build','pull'),('网页绑定','0.0.0.0','127.0.0.1')]:
        if not re.search(r'^\| '+label+r' \| '+re.escape(a)+r' \| '+re.escape(b)+r' \|$',cli,re.M): errors.append('CLI documented defaults changed: '+label)
    counts=[('metadata/build-inputs.lock.json','files',424),('metadata/game-assets.lock.json','assets',392),('metadata/levels.lock.json','levels',13)]
    resources=(root/'docs/reference/resources.md').read_text(encoding='utf8')
    for path,key,expected in counts:
        if len(json.loads((root/path).read_text(encoding='utf8'))[key])!=expected: errors.append('resource source count changed: '+path)
        if str(expected) not in resources: errors.append('resource documented count missing: '+str(expected))
    ports=(root/'docs/reference/ports.md').read_text(encoding='utf8')
    generator=(root/'scripts/diagnostics/ubuntu_ports.py').read_text(encoding='utf8')
    default=argument_defaults(generator).get('--channels')
    if default!='11,52' or '11,52' not in ports or '15,52' not in ports: errors.append('port profile example differs from source')
    current=json.loads((root/'docs/status/current.json').read_text(encoding='utf8'))
    errors+=check_status(root,current,files,strict=True)
    errors+=check_port_table(root)
    expected_ledgers=['services/admin/backend/src/main/resources/dof/scope.json','services/admin/backend/src/main/resources/dof/execution-plan.json']
    if current.get('completionLedgers')!=expected_ledgers: errors.append('completion ledger authority changed')
    for p in expected_ledgers:
        if p not in files: errors.append('completion ledger missing: '+p)
    deployment=(root/'docs/status/deployment.md').read_text(encoding='utf8')
    for name in ('applicationSourceCommit','adminImage','gameImage','adminJarSha256'):
        if current['facts'][name]['value'] not in deployment: errors.append('deployment prose differs from summary: '+name)
    if 'dof-launcher-reader-2.2' not in (root/'scripts/dof/client_reader.py').read_text(encoding='utf8'): errors.append('native recipe source changed')
    return errors

def audit(root,includes=()):
    files=tracked_files(root,includes)
    errors,graph=check_links(root,files)
    catalog=json.loads((Path(root)/'docs/catalog.json').read_text(encoding='utf8'))
    errors+=check_immutable(root,catalog['immutable'],files)
    errors+=check_baseline(root,catalog,files)
    errors+=check_contracts(root,catalog,files,graph)
    errors+=check_public_material(root,files)
    return errors,{'originalDocuments':len(catalog['originals']),'originalEvidence':sum(p.startswith('docs/') and p.endswith('.json') for p in catalog['immutable']),'registeredPages':len(catalog['pages']),'trackedFilesChecked':len(files)}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--include',action='append',default=[],help='explicit additional repo-relative file; never directories')
    args=parser.parse_args()
    try: errors,summary=audit(args.root,args.include)
    except (ValueError,KeyError,OSError,subprocess.CalledProcessError) as error:
        print('Documentation check could not run: '+str(error)); return 2
    if errors:
        print('\n'.join(errors)); print(f'FAIL: {len(errors)} documentation errors'); return 1
    print('PASS: '+json.dumps(summary,ensure_ascii=False)); return 0

if __name__=='__main__': sys.exit(main())
