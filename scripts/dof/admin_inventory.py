"""Validate/export the existing admin scope and work packages without marking work complete.

The generated report is a view of scope.json/execution-plan.json, not a second ledger.
Each verification stage needs explicit evidence; implementation labels do not imply acceptance.
"""
import argparse
import csv
import io
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / 'services/admin/backend/src/main/resources/dof'
STAGES = ('nativeParity', 'implementation', 'persistence', 'consumption', 'effect', 'recovery')
STATES = {'pending', 'blocked', 'passed', 'not_applicable'}


def inventory(scope, plan, minimum_classes=71, minimum_operations=239):
    packages = {p['id']: p for p in plan['packages']}
    if len(packages) != len(plan['packages']):
        raise ValueError('Duplicate work package')
    owners = {}
    for package in packages.values():
        for dependency in package['dependsOn']:
            if dependency not in packages:
                raise ValueError('Unknown work package dependency')
        if package['state'] == 'complete' and not package.get('completionEvidence'):
            raise ValueError('Completed work package has no evidence')
        for group in package['groupIds']:
            if group in owners:
                raise ValueError('Feature group assigned more than once')
            owners[group] = package['id']
    def visit(key, chain):
        if key in chain:
            raise ValueError('Cyclic work package dependency')
        for key2 in packages[key]['dependsOn']:
            visit(key2, chain | {key})
    for key in packages:
        visit(key, set())
    rows, identities, groups = [], set(), set()
    for group in scope['groups']:
        if group['id'] in groups or group['id'] not in owners:
            raise ValueError('Duplicate or unassigned feature group')
        groups.add(group['id'])
        for operation in group['operations']:
            if operation['id'] in identities or operation.get('included') is not True:
                raise ValueError('Duplicate or excluded operation')
            identities.add(operation['id'])
            declared = operation.get('verification', {})
            if set(declared) - set(STAGES):
                raise ValueError('Unknown verification stage')
            stages = {}
            for stage in STAGES:
                value = declared.get(stage, {'state': 'pending', 'evidence': []})
                state, evidence = value.get('state'), value.get('evidence', [])
                if state not in STATES or not isinstance(evidence, list) or any(not isinstance(e, str) or not e.strip() for e in evidence):
                    raise ValueError('Invalid verification state/evidence')
                if state in ('passed', 'not_applicable') and not evidence:
                    raise ValueError('Accepted verification stage has no evidence')
                if state == 'not_applicable' and not value.get('reason'):
                    raise ValueError('Not-applicable verification stage has no reason')
                stages[stage] = {'state': state, 'evidence': evidence, 'reason': value.get('reason')}
            complete = all(v['state'] in ('passed', 'not_applicable') for v in stages.values()) and operation.get('nativeMenuConfirmed') is True
            if operation.get('status') == 'complete' and not complete:
                raise ValueError('Completed operation lacks stage evidence or native menu confirmation')
            management = operation.get('managementImplementation') or {}
            package = packages[owners[group['id']]]
            # Export the source evidence verbatim. A label, package-level success
            # or subset must never promote an entire operation's verification.
            remaining = operation.get('remaining') or management.get('blockers') or package.get('remaining', [])
            if isinstance(remaining, str):
                remaining = [remaining]
            evidence = list(operation.get('evidence', []))
            for key in ('evidence', 'liveEvidence'):
                value = management.get(key)
                if isinstance(value, str) and value not in evidence:
                    evidence.append(value)
            rows.append({'id': operation['id'], 'group': group['id'], 'groupTitle': group['title'], 'name': operation['name'],
                         'workPackage': owners[group['id']], 'nativeMenuConfirmed': operation.get('nativeMenuConfirmed') is True,
                         'input': operation.get('input'), 'nativeLocation': operation.get('nativeLocation') or management.get('nativeLocation'),
                         'executor': operation.get('executor') or management.get('executor'),
                         'api': management.get('api'), 'entry': management.get('entry'),
                         'activation': operation.get('activation') or management.get('activation'),
                         'output': operation.get('output'), 'failureBehavior': operation.get('failureBehavior'),
                         'implementationLabel': operation.get('implementationStatus') or management.get('state'),
                         'dependencies': list(package['dependsOn']),
                         'remaining': remaining,
                         'nextStep': remaining[0] if remaining else '逐项核对六阶段证据，未核实不关闭门禁',
                         'nextStepSource': 'operation' if operation.get('remaining') else 'management' if management.get('blockers') else 'workPackage',
                         'evidence': evidence,
                         'implementationSubsets': operation.get('implementationSubsets', []),
                         'mappingGaps': [key for key, value in {
                             'entry': management.get('entry'), 'api': management.get('api'),
                             'nativeLocation': operation.get('nativeLocation') or management.get('nativeLocation'),
                             'executor': operation.get('executor') or management.get('executor')}.items() if not value],
                         'verification': stages, 'complete': complete})
    if set(owners) != groups:
        raise ValueError('Work package refers to an unknown feature group')
    if len(groups) < minimum_classes or len(rows) < minimum_operations:
        raise ValueError('Feature scope is below the agreed minimum')
    if scope.get('discoveredClasses') != len(groups) or scope.get('discoveredOperations') != len(rows):
        raise ValueError('Declared feature counts differ from actual scope')
    return {'schema': 1, 'sourceEdition': scope.get('sourceEdition'), 'groups': len(groups), 'operations': len(rows),
            'nativeMenuAuditComplete': scope.get('nativeMenuAuditComplete') is True,
            'completedOperations': sum(r['complete'] for r in rows),
            'completeReplacementEligible': bool(rows) and all(r['complete'] for r in rows)
                and scope.get('nativeMenuAuditComplete') is True
                and all(p['state'] == 'complete' for p in packages.values()),
            'rows': rows}


def csv_report(report):
    out = io.StringIO(); columns = ['id', 'groupTitle', 'name', 'workPackage', 'input', 'nativeLocation', 'executor', 'api', 'entry', 'activation', 'output', 'failureBehavior', 'implementationLabel', 'dependencies', 'nextStep', 'nextStepSource', 'mappingGaps', 'evidence', *STAGES, 'complete']
    writer = csv.DictWriter(out, fieldnames=columns); writer.writeheader()
    for row in report['rows']:
        record = {key: row.get(key) for key in columns}
        for key in ('dependencies', 'mappingGaps', 'evidence'):
            record[key] = json.dumps(record[key], ensure_ascii=False)
        for stage in STAGES:
            record[stage] = row['verification'][stage]['state']
        # Spreadsheet cells are data, including future operator-authored names.
        writer.writerow({key: "'" + value if isinstance(value, str) and value.startswith(('=', '+', '-', '@', '\t', '\r')) else value for key, value in record.items()})
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scope', type=Path, default=RESOURCES / 'scope.json')
    parser.add_argument('--plan', type=Path, default=RESOURCES / 'execution-plan.json')
    parser.add_argument('--format', choices=('json', 'csv'), default='json')
    parser.add_argument('--require-complete', action='store_true')
    args = parser.parse_args()
    try:
        report = inventory(json.loads(args.scope.read_text(encoding='utf-8')), json.loads(args.plan.read_text(encoding='utf-8')))
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(2, 'Invalid admin inventory: ' + str(error) + '\n')
    print(csv_report(report) if args.format == 'csv' else json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not args.require_complete or report['completeReplacementEligible'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
