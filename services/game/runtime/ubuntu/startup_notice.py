"""Report stable Qingkong service readiness; gameplay still requires client validation."""
import os
import subprocess
import sys
import time

def channel_names(env):
    if env.get('SERVER_TYPE','ALL') not in ('ALL','GAME'):
        return []
    group=('cain','diregie','siroco','prey','casillas','hilder')[int(env.get('SERVER_GROUP','3'))-1]
    return [group+f'{int(number):02d}' for number in env.get('OPEN_CHANNEL','11,52').split(',')]





def output(message):
    print(message,flush=True)


def make_check(health,env,log_root='/home/neople/game/log'):
    profile=env.get('LAUNCHER_MODE','qingkong')
    if profile != 'qingkong':
        raise ValueError('LAUNCHER_MODE must be qingkong')

    def check():
        health.check()
        processes,_=health.snapshot()
        # Ignore health helpers/shells whose short-lived PIDs naturally change.
        # An Qingkong service restart resets the three-sample window.
        fingerprint=tuple(sorted((pid,name,tuple(args)) for pid,name,args in processes
                                 if name.startswith('df_') or name in ('QK','gunnersvr','zergsvr','secagent')))
        if not fingerprint:
            raise ValueError('Qingkong processes disappeared after health check')
        return fingerprint
    return check


def wait_ready(check,message,timeout=600,emit=output):
    deadline=time.monotonic()+timeout
    consecutive=0
    previous=None
    reason='readiness checks not yet stable'
    while time.monotonic()<deadline:
        try:
            evidence=check()
            if consecutive and evidence!=previous:
                consecutive=0
            previous=evidence
            consecutive+=1
            if consecutive==3:
                emit(message)
                return 0
            reason='readiness checks not yet stable'
        except (ValueError,OSError,KeyError,subprocess.TimeoutExpired) as exc:
            consecutive=0
            reason=str(exc)
        time.sleep(2)
    emit(f'[启动未完成] 等待 {timeout} 秒超时：{reason}')
    return 1


def main():
    import health
    try:
        check=make_check(health,os.environ)
    except ValueError as exc:
        output('[启动未完成] '+str(exc))
        return 1
    role=os.environ.get('SERVER_TYPE','ALL')
    names=channel_names(os.environ)
    detail=role+('；频道：'+'、'.join(names) if names else '')
    message=f'[启动完成] 服务健康检查已连续稳定通过（{detail}）；尚未验证客户端实玩流程'
    return wait_ready(check,message,int(os.environ.get('STARTUP_TIMEOUT','600')))


if __name__=='__main__':
    sys.exit(main())
