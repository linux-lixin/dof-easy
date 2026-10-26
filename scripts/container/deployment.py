"""Resolve one Compose model, then remove the internal DB for external instances."""
import copy
import json


def configure(model, values, root, databases):
    model = copy.deepcopy(model)
    services = model['services']
    external = values.get('DOF_DATABASE_KIND', 'internal') == 'external'
    native = values.get('LAUNCHER_MODE', 'dof') == 'native'
    if external:
        services.pop('db', None)
        model.get('volumes', {}).pop('database', None)
        for service in services.values():
            service.get('depends_on', {}).pop('db', None)
        model['networks']['public']['ipam'] = {'config':[{'subnet':values['DOF_DB_SUBNET']}]}
        database_env = {key:values[key] for key in ('DOF_DB_HOST','DOF_DB_PORT','DOF_DATABASE_UUID')}
        database_env['DNF_DB_GAME_PASSWORD'] = values['GAME_DB_PASSWORD']
        database_env['DOF_BACKUP_DATABASES'] = json.dumps(databases)
        helper = dict(image=services['game']['image'],pull_policy='never',
            entrypoint=['python3','/opt/dnf/ubuntu/external_database.py'],
            environment=database_env, networks={'public':None},read_only=True,
            tmpfs=['/tmp'],cap_drop=['ALL'],security_opt=['no-new-privileges:true'])
        services['db-tools'] = dict(copy.deepcopy(helper),profiles=['tools'],command=['check'])
        env=services['game']['environment']
        env.update(database_env)
        env.update(DOF_DATABASE_MODE='existing', DOF_DATABASE_KIND='external',
                   MAIN_MYSQL_HOST=values['DOF_DB_HOST'],MYSQL_HOST=values['DOF_DB_HOST'],
                   MAIN_MYSQL_PORT=values['DOF_DB_PORT'],MYSQL_PORT=values['DOF_DB_PORT'],
                   MAIN_MYSQL_ROOT_PASSWORD='unused',DNF_DB_ROOT_PASSWORD='unused',
                   MAIN_MYSQL_GAME_ALLOW_IP=values['DOF_DB_ALLOW_HOST'],MYSQL_GAME_ALLOW_IP=values['DOF_DB_ALLOW_HOST'])
        services['admin']['networks']['public']=None
        services['admin']['environment'].update(
            SPRING_DATASOURCE_URL='jdbc:mysql://'+values['DOF_DB_HOST']+':'+values['DOF_DB_PORT']+'/dnf_service?useUnicode=true&characterEncoding=utf8&useSSL=false',
            SPRING_DATASOURCE_USERNAME='game',SPRING_DATASOURCE_PASSWORD=values['GAME_DB_PASSWORD'])
    if native:
        admin=services['admin']
        admin['entrypoint']=['/app/dof-entrypoint.sh']
        env=admin['environment']
        env.update(DOF_ADMIN_ENABLED='true',DOF_ENVIRONMENT='isolated',
                   DOF_INSTANCE_ID='dof-'+root.name,DOF_DATABASE_UUID=values.get('DOF_DATABASE_UUID',''),
                   DOF_ADAPTER_VERSION='dof-native-1',LAUNCHER_MODE='native',
                   DOF_CLIENT_WORKSPACE='/app/data/dof-client-publication',DOF_GAME_PVF='/app/paired/Script.pvf',
                   SPRING_DATASOURCE_USERNAME='game',SPRING_DATASOURCE_PASSWORD=values['GAME_DB_PASSWORD'])
        for key in ('ADMIN_PASSWORD','ADMIN_TEMP_SECRET'):
            env.pop(key,None)
        admin['volumes'].append(dict(type='bind',source=str(root/'runtime/game/data/Script.pvf'),target='/app/paired/Script.pvf',read_only=True))
    return model


def literal_model(value):
    """Resolved Compose strings must survive the second interpolation pass."""
    if isinstance(value, str):
        return value.replace('$', '$$')
    if isinstance(value, list):
        return [literal_model(item) for item in value]
    if isinstance(value, dict):
        return {key: literal_model(item) for key, item in value.items()}
    return value
