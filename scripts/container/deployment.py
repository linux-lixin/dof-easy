"""Resolve the original Qingkong game and external database tooling."""
import copy
import json


def configure(model, values, root, databases, previous=None):
    if values.get('LAUNCHER_MODE') != 'qingkong' or values.get('DOF_DATABASE_KIND') != 'external':
        raise ValueError('Only qingkong with external MySQL is supported')
    if values.get('PLUGIN_MODE', 'none') != 'none' or values.get('RAID_EXTENSION', 'off') != 'off':
        raise ValueError('Original qingkong requires plugins none and raid off')
    model = copy.deepcopy(model)
    services = model['services']
    services.pop('admin', None)
    services.pop('db', None)
    model.get('volumes', {}).pop('database', None)
    services['keys']['image'] = services['game']['image']
    for service in services.values():
        service.get('depends_on', {}).pop('db', None)
    model['networks']['public']['ipam'] = {'config':[{'subnet':values['DOF_DB_SUBNET']}]}
    database_env = {key:values.get(key, '') for key in ('DOF_DB_HOST','DOF_DB_PORT','DOF_DATABASE_UUID')}
    database_env['DNF_DB_GAME_PASSWORD'] = values['GAME_DB_PASSWORD']
    database_env['DOF_BACKUP_DATABASES'] = json.dumps(databases)
    services['db-tools'] = dict(image=services['game']['image'],pull_policy='never',
        entrypoint=['python3','/opt/dnf/ubuntu/external_database.py'],
        environment=database_env, networks={'public':None},read_only=True,
        tmpfs=['/tmp'],cap_drop=['ALL'],security_opt=['no-new-privileges:true'],
        profiles=['tools'],command=['check'])
    env=services['game']['environment']
    env.update(database_env)
    env.update(DOF_DATABASE_MODE='existing', DOF_DATABASE_KIND='external',
               MAIN_MYSQL_HOST=values['DOF_DB_HOST'],MYSQL_HOST=values['DOF_DB_HOST'],
               MAIN_MYSQL_PORT=values['DOF_DB_PORT'],MYSQL_PORT=values['DOF_DB_PORT'],
               MAIN_MYSQL_ROOT_PASSWORD='unused',DNF_DB_ROOT_PASSWORD='unused',
               MAIN_MYSQL_GAME_ALLOW_IP=values['DOF_DB_ALLOW_HOST'],MYSQL_GAME_ALLOW_IP=values['DOF_DB_ALLOW_HOST'])
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
