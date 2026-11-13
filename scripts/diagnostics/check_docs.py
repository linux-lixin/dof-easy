#!/usr/bin/env python3
"""Offline documentation links, catalog, defaults and publication boundaries."""
import argparse
import ast
import json
from pathlib import Path
import posixpath
import re
import subprocess
from urllib.parse import unquote, urlsplit

TEXT_EXTENSIONS={'.md','.txt','.rst','.adoc'}

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

def argument_defaults(text):
    result={}
    for node in ast.walk(ast.parse(text)):
        if not isinstance(node,ast.Call) or not isinstance(node.func,ast.Attribute) or node.func.attr!='add_argument': continue
        names=[a.value for a in node.args if isinstance(a,ast.Constant) and isinstance(a.value,str)]
        for keyword in node.keywords:
            if keyword.arg=='default' and isinstance(keyword.value,ast.Constant):
                for name in names: result[name]=str(keyword.value.value)
    return result

def new_install_defaults(root):
    defaults={}
    sources=[('scripts/container/manage.py', {'launcher':'--launcher', 'args.level':'--level'}),
             ('scripts/container/database.py', {'mode':'--database'})]
    for path, names in sources:
        tree=ast.parse((Path(root)/path).read_text(encoding='utf8'))
        for node in ast.walk(tree):
            if not isinstance(node,ast.Assign) or not isinstance(node.value,ast.BoolOp) or not isinstance(node.value.op,ast.Or): continue
            last=node.value.values[-1]
            if not isinstance(last,ast.Constant): continue
            for target in node.targets:
                name=target.id if isinstance(target,ast.Name) else (target.value.id+'.'+target.attr if isinstance(target,ast.Attribute) and isinstance(target.value,ast.Name) else '')
                if name in names: defaults[names[name]]=str(last.value)
    return defaults


def check_default_table(root, rows=None):
    rows=rows or [('等级','--level'),('频道','--channels'),('游戏绑定','--bind'),('登录器','--launcher'),('数据库','--database'),('插件','--plugins')]
    root=Path(root); errors=[]
    defaults=argument_defaults((root/'scripts/container/manage.py').read_text(encoding='utf8'))
    if (root/'scripts/container/database.py').is_file(): defaults.update(new_install_defaults(root))
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

def maintained_pages(files):
    return {p for p in files if p.endswith('.md') and not p.startswith('services/game/assets/')}


def check_catalog(pages, files):
    paths=[p['path'] for p in pages]
    errors=[]
    if len(paths)!=len(set(paths)): errors.append('duplicate catalog pages')
    for p in sorted(maintained_pages(files)-set(paths)): errors.append('unregistered page: '+p)
    for p in sorted(set(paths)-files): errors.append('missing catalog page: '+p)
    if any(p.get('kind')!='current' for p in pages): errors.append('only current documentation belongs in this branch')
    return errors


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--include',action='append',default=[])
    args=parser.parse_args()
    files=tracked_files(args.root,args.include)
    catalog=json.loads((args.root/'docs/catalog.json').read_text(encoding='utf8'))
    errors,graph=check_links(args.root, files)
    errors+=check_catalog(catalog['pages'],files)
    errors+=check_navigation(catalog['pages'],graph)
    errors+=check_default_table(args.root)
    errors+=check_public_material(args.root,files)
    for error in errors: print(error)
    print(json.dumps({'trackedFiles':len(files),'pages':len(catalog['pages']),'errors':len(errors)}))
    return bool(errors)


if __name__=='__main__':
    raise SystemExit(main())
